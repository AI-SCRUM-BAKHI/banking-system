package com.scrumai.fraud.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scrumai.common.exception.GlobalExceptionHandler;
import com.scrumai.fraud.controller.TransactionController;
import com.scrumai.fraud.dto.EvaluationRequest;
import com.scrumai.fraud.repository.RiskEvaluationRepository;
import com.scrumai.fraud.service.IpIntelligenceService;
import com.scrumai.fraud.service.RiskScoreEngine;
import com.scrumai.fraud.service.TransactionEvaluationService;
import com.scrumai.fraud.service.TransactionIngestService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Uses isolated schemas in an explicitly supplied disposable PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "SCAN_TEST_DATABASE_URL", matches = ".+")
class TransactionScanPostgresTest {
  private static final Instant T = Instant.parse("2026-10-01T19:30:00Z");
  private String schema;
  private JdbcTemplate admin;
  private JdbcTemplate jdbc;
  private DriverManagerDataSource source;
  private TransactionScanRepository repository;
  private TransactionScanProcessor processor;

  @BeforeEach
  void setup() throws Exception {
    String url = System.getenv("SCAN_TEST_DATABASE_URL");
    String user = System.getenv().getOrDefault("SCAN_TEST_DATABASE_USER", "postgres");
    String password = System.getenv().getOrDefault("SCAN_TEST_DATABASE_PASSWORD", "");
    admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    schema = "scan_test_" + UUID.randomUUID().toString().replace("-", "");
    admin.execute("CREATE SCHEMA " + schema);
    source =
        new DriverManagerDataSource(
            url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, password);
    jdbc = new JdbcTemplate(source);
    for (String file :
        List.of(
            "V1__initialize_database.sql",
            "V2__create_identity_and_fraud_schema.sql",
            "V3__create_auth_refresh_tokens.sql",
            "V4__create_risk_evaluation_context.sql",
            "V5__create_transaction_scan.sql",
            "V6__create_dataset_import_tables.sql",
            "V7__create_customers.sql",
            "V8__consolidate_transaction_data.sql",
            "V9__remove_redundant_transaction_tables.sql")) {
      jdbc.execute(
          new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8));
    }
    var mapper = JsonMapper.builder().build();
    repository =
        spy(
            new TransactionScanRepository(
                jdbc, mapper, new RiskEvaluationRepository(jdbc, mapper)));
    processor =
        new TransactionScanProcessor(
            repository,
            new RiskScoreEngine(),
            JsonMapper.builder().build(),
            new DataSourceTransactionManager(source));
  }

  @AfterEach
  void cleanup() {
    if (schema != null) {
      admin.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  @Test
  void partialAndBackfillCompleteWithoutMutatingLegacyWatermark() {
    insert("normal", "A", "B", 100, T, null);
    jdbc.update(
        "UPDATE risk_ingest_state SET last_transaction_time_exact = ?",
        T.plusSeconds(100).toString());
    assertThat(processor.process("normal")).isEqualTo("COMPLETED");
    assertThat(value("normal", "evaluation_decision")).isEqualTo("INSUFFICIENT_DATA");
    assertThat(value("normal", "evaluation_status")).isEqualTo("PARTIAL");
    assertThat(count("risk_transfer_history")).isEqualTo(1);
    assertThat(count("fraud_transactions")).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT last_transaction_time_exact FROM risk_ingest_state", String.class))
        .isEqualTo(T.plusSeconds(100).toString());
    assertThat(processor.process("normal")).isEqualTo("SKIPPED");
  }

  @Test
  void fraudIsAtomicAndResetAppendsHistoryWithoutChangingReview() {
    fraudFixture();
    assertThat(processor.process("suspect")).isEqualTo("COMPLETED");
    assertThat(value("suspect", "risk_score")).isEqualTo("75.00");
    assertThat(count("fraud_transactions")).isEqualTo(1);
    assertThat(count("fraud_rule_hits")).isEqualTo(3);
    assertThat(count("transaction_chain")).isEqualTo(1);
    jdbc.update("UPDATE fraud_transactions SET review_status = 'CONFIRMED_FRAUD'");
    jdbc.update(
        "UPDATE transactions SET processing_status = 'PENDING' WHERE transaction_ref = 'suspect'");
    assertThat(processor.process("suspect")).isEqualTo("COMPLETED");
    assertThat(count("risk_transfer_history")).isEqualTo(2);
    assertThat(count("fraud_transactions")).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT review_status FROM fraud_transactions", String.class))
        .isEqualTo("CONFIRMED_FRAUD");
    assertThatThrownBy(
            () ->
                jdbc.update("UPDATE transactions SET amount = 1 WHERE transaction_ref = 'suspect'"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE transactions SET posted_at = transaction_time WHERE transaction_ref = 'suspect'"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rollbackAndRetryDoNotLeavePartialFraudOrAudit() {
    fraudFixture();
    doAnswer(
            call -> {
              call.callRealMethod();
              throw new TransientDataAccessResourceException("simulated failure after all writes");
            })
        .when(repository)
        .save(any(), any(), any(), any(), any(), any());
    long[] delays = {30, 120, 600};
    for (int attempt = 1; attempt <= 4; attempt++) {
      Instant before = Instant.now();
      assertThat(processor.process("suspect")).isEqualTo(attempt < 4 ? "RETRY" : "FAILED");
      assertThat(value("suspect", "attempt_count")).isEqualTo(Integer.toString(attempt));
      if (attempt < 4) {
        Instant next =
            jdbc.queryForObject(
                "SELECT next_attempt_at FROM transactions WHERE transaction_ref='suspect'",
                (rs, row) -> rs.getTimestamp(1).toInstant());
        assertThat(next).isAfterOrEqualTo(before.plusSeconds(delays[attempt - 1]));
        assertThat(repository.candidates(100)).doesNotContain("suspect");
        jdbc.update(
            "UPDATE transactions SET next_attempt_at = now() - interval '1 second' WHERE transaction_ref='suspect'");
      }
      assertThat(count("risk_transfer_history")).isZero();
      assertThat(count("fraud_transactions")).isZero();
      assertThat(count("fraud_rule_hits")).isZero();
      assertThat(count("transaction_chain")).isZero();
    }
  }

  @Test
  void invalidDataFailsWithoutRetryAndNextCandidateStillRuns() {
    insert("invalid", "A", "B", 0, T, null);
    insert("valid", "C", "D", 100, T, null);
    assertThat(processor.process("invalid")).isEqualTo("FAILED");
    assertThat(value("invalid", "attempt_count")).isEqualTo("1");
    assertThat(processor.process("valid")).isEqualTo("COMPLETED");
  }

  @Test
  void contextUsesSourceTimeAndStableTiesNotEvaluationHistory() {
    insert("current", "A", "B", 100, T, null);
    insert("aaa", "A", "B", 100, T, null);
    insert("zzz", "A", "B", 100, T, null);
    insert("future", "A", "B", 100, T.plusSeconds(1), null);
    insert("older", "A", "B", 100, T.minusSeconds(1), null);
    insert("incoming", "C", "A", 100, T.minusSeconds(120), T.minusSeconds(90));
    insert("unknown", "D", "A", 100, T.minusSeconds(60), null);
    jdbc.update(
        "UPDATE transactions SET processing_status = 'FAILED' WHERE transaction_ref = 'older'");
    new TransactionTemplate(new DataSourceTransactionManager(source))
        .executeWithoutResult(
            ignored -> {
              var context = repository.context(repository.lock("current").orElseThrow());
              assertThat(context.outbound())
                  .extracting(tx -> tx.transactionRef())
                  .containsExactly("older", "aaa");
              assertThat(context.inbound())
                  .extracting(tx -> tx.transactionRef())
                  .containsExactly("incoming");
              assertThat(context.unknownPosting()).isTrue();
              assertThat(context.coverage()).isNull();
            });
  }

  @Test
  void advisoryLockExcludesOtherInstancesAndReleasesAfterException() throws Exception {
    var first = new TransactionScanLock(source);
    var second = new TransactionScanLock(source);
    try (var lease = first.tryAcquire()) {
      assertThat(lease).isNotNull();
      lease.verify();
      assertThat(second.tryAcquire()).isNull();
    }
    assertThatThrownBy(
            () -> {
              try (var lease = first.tryAcquire()) {
                lease.verify();
                throw new IllegalStateException("simulated job failure");
              }
            })
        .isInstanceOf(IllegalStateException.class);
    try (var lease = second.tryAcquire()) {
      assertThat(lease).isNotNull();
    }
  }

  @Test
  void migrationEnforcesJsonConstraintsAndUpdatesTimestamp() {
    insert("normal", "A", "B", 100, T, null);
    assertThatThrownBy(() -> jdbc.update("UPDATE transactions SET rule_hits = '{}'::jsonb"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("UPDATE transactions SET processing_status = 'UNKNOWN'"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("UPDATE transactions SET attempt_count = -1"))
        .isInstanceOf(DataIntegrityViolationException.class);
    var before =
        jdbc.queryForObject("SELECT updated_at FROM transactions", java.sql.Timestamp.class);
    jdbc.update("UPDATE transactions SET last_error = 'test'");
    assertThat(jdbc.queryForObject("SELECT updated_at FROM transactions", java.sql.Timestamp.class))
        .isAfter(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = ? AND tablename = 'transactions'",
                Integer.class,
                schema))
        .isGreaterThanOrEqualTo(8);
  }

  @Test
  void flowRulesUsePostedTimeWithCertifiedCoverage() {
    Instant noon = Instant.parse("2026-10-01T05:00:00Z");
    insert("in", "C", "A", 100, noon.minusSeconds(200), noon.minusSeconds(90));
    insert("out", "A", "B", 96, noon, null);
    jdbc.update(
        """
        INSERT INTO risk_account_coverage(account_id, outbound_from, outbound_through,
          inbound_posted_from, inbound_posted_through, posting_complete)
        VALUES ('A', ?, ?, ?, ?, TRUE)
        """,
        java.sql.Timestamp.from(noon.minusSeconds(86400)),
        java.sql.Timestamp.from(noon),
        java.sql.Timestamp.from(noon.minusSeconds(86400)),
        java.sql.Timestamp.from(noon));
    assertThat(processor.process("out")).isEqualTo("COMPLETED");
    assertThat(value("out", "risk_score")).isEqualTo("65.00");
    assertThat(count("fraud_transactions")).isEqualTo(1);
  }

  @Test
  void adoptsPreviouslyCreatedSourceTableWithoutLosingRows() throws Exception {
    jdbc.execute("DROP TABLE transactions");
    jdbc.execute("DROP FUNCTION protect_transaction_source()");
    String migration =
        new ClassPathResource("db/migration/V5__create_transaction_scan.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    jdbc.execute(migration.substring(0, migration.indexOf("-- Also adopt")));
    jdbc.update(
        """
        INSERT INTO transactions(transaction_ref, from_account_ref, from_account_masked,
          to_account_ref, to_account_masked, amount, transaction_time)
        VALUES ('existing', 'A', '***A', 'B', '***B', 100, now())
        """);
    jdbc.execute(migration);
    assertThat(value("existing", "processing_status")).isEqualTo("PENDING");
    assertThat(processor.process("existing")).isEqualTo("COMPLETED");
  }

  @Test
  void concurrentWorkersCreateOnlyOneEvaluation() throws Exception {
    insert("normal", "A", "B", 100, T, null);
    try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<String> task =
          () -> {
            start.await();
            return processor.process("normal");
          };
      var one = workers.submit(task);
      var two = workers.submit(task);
      start.countDown();
      assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder("COMPLETED", "SKIPPED");
    }
    assertThat(count("risk_transfer_history")).isEqualTo(1);
  }

  @Test
  void existingApiCaseIsPreserved() {
    fraudFixture();
    jdbc.update(
        """
        INSERT INTO fraud_transactions(transaction_ref, from_account_ref, from_account_masked,
          to_account_ref, to_account_masked, amount, currency, transaction_time,
          risk_score, risk_level, fraud_reason, review_status)
        SELECT transaction_ref, from_account_ref, from_account_masked, to_account_ref,
          to_account_masked, amount, currency, transaction_time, 1, 'LOW', 'legacy', 'FALSE_POSITIVE'
        FROM transactions WHERE transaction_ref = 'suspect'
        """);
    assertThat(processor.process("suspect")).isEqualTo("COMPLETED");
    assertThat(count("risk_transfer_history")).isEqualTo(1);
    assertThat(count("fraud_transactions")).isEqualTo(1);
    assertThat(count("fraud_rule_hits")).isZero();
    assertThat(jdbc.queryForObject("SELECT review_status FROM fraud_transactions", String.class))
        .isEqualTo("FALSE_POSITIVE");
  }

  @Test
  void participatesInRuntimeJpaTransactionManager() {
    var factory = new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(source);
    factory.setPackagesToScan("com.scrumai.fraud.entity");
    factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
    factory.afterPropertiesSet();
    try {
      var manager = new org.springframework.orm.jpa.JpaTransactionManager(factory.getObject());
      manager.setDataSource(source);
      var runtimeProcessor =
          new TransactionScanProcessor(
              repository, new RiskScoreEngine(), JsonMapper.builder().build(), manager);
      fraudFixture();
      assertThat(runtimeProcessor.process("suspect")).isEqualTo("COMPLETED");
      assertThat(count("fraud_transactions")).isEqualTo(1);
    } finally {
      factory.destroy();
    }
  }

  @Test
  void rejectsAllUnsupportedSourceShapesWithoutTruncation() {
    List<String> changes =
        List.of(
            "amount = 0",
            "amount = 0.50",
            "amount = 1000000000000000",
            "currency = 'USD'",
            "from_account_ref = repeat('a', 81)",
            "to_account_ref = repeat('b', 81)",
            "from_account_masked = repeat('*', 41)",
            "to_account_masked = repeat('*', 41)",
            "from_customer_name = repeat('n', 121)",
            "to_customer_name = repeat('n', 121)",
            "to_account_ref = from_account_ref",
            "transaction_time = now() + interval '1 day'",
            "posted_at = transaction_time - interval '1 second'",
            "posted_at = now() + interval '1 day'");
    for (int i = 0; i < changes.size(); i++) {
      String ref = "invalid-" + i;
      insert(ref, "A" + i, "B" + i, 100, T, null);
      jdbc.update("UPDATE transactions SET " + changes.get(i) + " WHERE transaction_ref = ?", ref);
      assertThat(processor.process(ref)).as(changes.get(i)).isEqualTo("FAILED");
    }
    assertThat(count("risk_transfer_history")).isZero();
  }

  @Test
  void jobHonorsLockBatchLimitAndCanResumeWithANewInstance() throws Exception {
    insert("one", "A", "B", 100, T, null);
    insert("two", "C", "D", 100, T, null);
    var lock = new TransactionScanLock(source);
    var job = new TransactionScanJob(lock, repository, processor, 1);
    try (var lease = lock.tryAcquire()) {
      lease.verify();
      job.scan();
      assertThat(count("risk_transfer_history")).isZero();
    }
    job.scan();
    assertThat(count("risk_transfer_history")).isEqualTo(1);
    new TransactionScanJob(new TransactionScanLock(source), repository, processor, 1).scan();
    assertThat(count("risk_transfer_history")).isEqualTo(2);
  }

  private void fraudFixture() {
    for (int i = 0; i < 20; i++) {
      insert("baseline-" + i, "A", "B", 100, T.minusSeconds(86400L + i), null);
    }
    insert("suspect", "A", "NEW", 100000, T, null);
    jdbc.update(
        """
        INSERT INTO risk_account_coverage(account_id, outbound_from, outbound_through)
        VALUES ('A', ?, ?)
        """,
        java.sql.Timestamp.from(T.minusSeconds(31L * 86400)),
        java.sql.Timestamp.from(T));
  }

  @Test
  void sharedHistoryDeduplicatesContextAndIpAndSupportsApiRetriesAfterJob() {
    fraudFixture();
    processor.process("suspect");
    jdbc.update(
        "UPDATE transactions SET processing_status='PENDING' WHERE transaction_ref='suspect'");
    processor.process("suspect");
    assertThat(count("risk_transfer_history")).isEqualTo(2);
    var mapper = JsonMapper.builder().build();
    var history = new RiskEvaluationRepository(jdbc, mapper);
    var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    transaction.executeWithoutResult(
        ignored -> {
          var context =
              history.context(
                  new com.scrumai.fraud.service.RiskModels.Transfer(
                      "next", "A", "B", BigDecimal.TEN, "VND", T.plusSeconds(1), null),
                  history.lock());
          assertThat(context.outbound()).hasSize(1);
          var api = new TransactionEvaluationService(history, new RiskScoreEngine(), mapper);
          var result =
              api.evaluate(
                  new EvaluationRequest(
                      "suspect",
                      "A",
                      "NEW",
                      BigDecimal.valueOf(100000),
                      "VND",
                      T.atOffset(java.time.ZoneOffset.UTC),
                      null));
          assertThat(result.riskScore()).isEqualTo(75);
        });
    assertThat(count("risk_transfer_history")).isEqualTo(2);
    jdbc.update(
        """
        UPDATE transactions SET source_data='{"ip_address":"203.0.113.1"}',
          source_label='{"is_fraud":"1"}' WHERE transaction_ref='suspect'
        """);
    var ip = new IpIntelligenceService(jdbc).load();
    assertThat(ip.totalTransactions()).isEqualTo(1);
    assertThat(ip.groups().getFirst().count()).isEqualTo(1);
    assertThat(ip.groups().getFirst().sourceFraud()).isEqualTo(1);
    assertThat(ip.groups().getFirst().engineFraud()).isEqualTo(1);
  }

  @Test
  void postSourceThenScanCreatesFraudAndRejectsDuplicateWithoutOverwriting() throws Exception {
    fraudFixture();
    jdbc.update("DELETE FROM transactions WHERE transaction_ref = 'suspect'");
    String body =
        """
        {"transactionRef":"suspect","fromAccountRef":"A","fromAccountMasked":"****1",
         "toAccountRef":"NEW","toAccountMasked":"****2","amount":100000.00,
         "currency":"VND","transactionTime":"2026-10-01T19:30:00Z",
         "riskScore":100,"riskLevel":"CRITICAL","processingStatus":"COMPLETED",
         "ruleHits":[{"ruleCode":"FAKE"}],"fraudReason":"injected"}
        """;
    MockMvc mvc = ingestMvc();
    mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.processingStatus").value("PENDING"));
    assertThat(value("suspect", "risk_score")).isEqualTo("0.00");
    assertThat(value("suspect", "rule_hits")).isEqualTo("[]");
    assertThat(count("fraud_transactions")).isZero();
    mvc.perform(
            post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("100000.00", "100001")))
        .andExpect(status().isConflict());
    assertThat(value("suspect", "amount")).isEqualTo("100000.00");
    new TransactionScanJob(new TransactionScanLock(source), repository, processor, 100).scan();
    assertThat(value("suspect", "processing_status")).isEqualTo("COMPLETED");
    assertThat(value("suspect", "risk_score")).isEqualTo("75.00");
    // The source baseline is also queued; its dense transfers may trigger frequency rules.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM fraud_transactions WHERE transaction_ref = 'suspect'",
                Integer.class))
        .isEqualTo(1);
    assertThat(count("risk_transfer_history")).isEqualTo(21);
  }

  @Test
  void postSourceRejectsFractionalAmountsAndInvalidAccountOrTimeRelationships() throws Exception {
    String body =
        """
        {"transactionRef":"invalid","fromAccountRef":"A","fromAccountMasked":"****1",
         "toAccountRef":"B","toAccountMasked":"****2","amount":100,
         "currency":"VND","transactionTime":"2026-10-01T08:00:00Z"}
        """;
    MockMvc mvc = ingestMvc();
    for (String invalid :
        List.of(
            body.replace("\"amount\":100", "\"amount\":1.5"),
            body.replace("\"toAccountRef\":\"B\"", "\"toAccountRef\":\"A\""),
            body.replace("2026-10-01T08:00:00Z", "2999-10-01T08:00:00Z"),
            body.replace("}", ",\"postedAt\":\"2026-10-01T07:59:00Z\"}"))) {
      mvc.perform(
              post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(invalid))
          .andExpect(status().isBadRequest());
    }
    assertThat(count("transactions")).isZero();
  }

  private MockMvc ingestMvc() {
    return MockMvcBuilders.standaloneSetup(
            new TransactionController(new TransactionIngestService(jdbc)))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  private void insert(
      String ref, String from, String to, long amount, Instant time, Instant posted) {
    jdbc.update(
        """
        INSERT INTO transactions(transaction_ref, from_account_ref, from_account_masked,
          to_account_ref, to_account_masked, amount, transaction_time, posted_at)
        VALUES (?, ?, '****1', ?, '****2', ?, ?, ?)
        """,
        ref,
        from,
        to,
        BigDecimal.valueOf(amount),
        java.sql.Timestamp.from(time),
        posted == null ? null : java.sql.Timestamp.from(posted));
  }

  private String value(String ref, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + "::text FROM transactions WHERE transaction_ref = ?",
        String.class,
        ref);
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }
}
