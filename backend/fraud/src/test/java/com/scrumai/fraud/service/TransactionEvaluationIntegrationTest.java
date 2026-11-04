package com.scrumai.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.scrumai.common.exception.BusinessException;
import com.scrumai.fraud.dto.EvaluationRequest;
import com.scrumai.fraud.repository.RiskEvaluationRepository;
import com.scrumai.fraud.service.RiskModels.Result;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TransactionEvaluationIntegrationTest {
  private static final Instant T = Instant.parse("2026-10-01T05:00:00Z");
  private JdbcTemplate jdbc;
  private TransactionTemplate transaction;
  private TransactionEvaluationService service;
  private RiskEvaluationRepository repository;
  private ObjectMapper mapper;

  @BeforeEach
  void setup() throws Exception {
    DriverManagerDataSource source =
        new DriverManagerDataSource(
            "jdbc:h2:mem:"
                + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
            "sa",
            "");
    jdbc = new JdbcTemplate(source);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    String migration =
        new ClassPathResource("db/migration/V4__create_risk_evaluation_context.sql")
            .getContentAsString(StandardCharsets.UTF_8)
            .replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE");
    for (String statement : migration.split(";")) {
      if (!statement.isBlank()) {
        jdbc.execute(statement);
      }
    }
    mapper = JsonMapper.builder().build();
    repository = new RiskEvaluationRepository(jdbc, mapper);
    service = new TransactionEvaluationService(repository, new RiskScoreEngine(), mapper);
  }

  @Test
  void persistsNormalHistoryAuditAndOriginalRetryBeforeCheckingWatermark() {
    EvaluationRequest first = request("first", T, "A", "B", 100, T);
    Result original = evaluate(first);
    evaluate(request("later", T.plusSeconds(1), "A", "B", 100, null));
    EvaluationRequest equivalent =
        new EvaluationRequest(
            "first",
            "A",
            "B",
            new BigDecimal("100.0"),
            "VND",
            first.transactionTime().withOffsetSameInstant(ZoneOffset.ofHours(7)),
            first.postedAt().withOffsetSameInstant(ZoneOffset.ofHours(7)));
    assertThat(evaluate(equivalent)).isEqualTo(original);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_transfer_history", Long.class))
        .isEqualTo(2);
    String audit =
        jdbc.queryForObject(
            "SELECT audit_json FROM risk_transfer_history WHERE transaction_ref = 'first'",
            String.class);
    assertThat(mapper.readTree(audit).get("rules").size()).isEqualTo(7);
    assertThat(mapper.readTree(audit).get("configuration").get("ruleVersion").asString())
        .isEqualTo("rule-score-v1");
    assertThat(original.isFraud()).isNull();
  }

  @Test
  void conflictsAndLateEventsDoNotMutateState() {
    evaluate(request("one", T, "A", "B", 100, null));
    assertThatThrownBy(() -> evaluate(request("one", T, "A", "B", 101, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("different data");
    assertThatThrownBy(() -> evaluate(request("late", T.minusNanos(1), "A", "B", 100, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Late transaction");
    assertThat(jdbc.queryForObject("SELECT last_ingest_order FROM risk_ingest_state", Long.class))
        .isEqualTo(1);
  }

  @Test
  void nanosecondTimesRemainExactDespiteDatabaseTimestampPrecision() {
    Instant precise = T.plusNanos(123456789);
    evaluate(request("precise", precise, "A", "B", 100, precise));
    evaluate(request("same", precise, "A", "B", 100, null));
    assertThatThrownBy(() -> evaluate(request("late", precise.minusNanos(1), "A", "B", 100, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Late transaction");
    assertThat(
            transaction
                .execute(
                    status ->
                        repository.context(
                            new RiskModels.Transfer(
                                "next", "A", "B", BigDecimal.TEN, "VND", precise, null),
                            repository.lock()))
                .outbound()
                .getFirst()
                .transactionTime())
        .isEqualTo(precise);
  }

  @Test
  void simultaneousRequestsSeeEachOtherAndRetryAddsNoCount() throws Exception {
    certify(T.minusSeconds(3600), T);
    List<Callable<Result>> requests = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      String ref = "concurrent" + i;
      requests.add(() -> evaluate(request(ref, T, "A", "B", 100, null)));
    }
    try (var executor = Executors.newFixedThreadPool(4)) {
      List<Result> results = new ArrayList<>();
      for (var future : executor.invokeAll(requests)) {
        results.add(future.get());
      }
      assertThat(results).filteredOn(r -> r.riskScore() == 45).hasSize(1);
      assertThat(results).filteredOn(r -> r.riskScore() == 0).hasSize(11);
      for (Result result : results) {
        assertThat(evaluate(request(result.transactionRef(), T, "A", "B", 100, null)))
            .isEqualTo(result);
      }
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_transfer_history", Long.class))
        .isEqualTo(12);
  }

  @Test
  void readsDelayedInboundByPostingTimeAndHonorsPostingCoverage() {
    evaluate(request("incoming", T.minusSeconds(86400), "C", "A", 100, T.minusSeconds(60)));
    certify(T.minusSeconds(3600), T);
    Result result = evaluate(request("outgoing", T, "A", "B", 95, null));
    assertThat(result.riskScore()).isEqualTo(65);
    assertThat(result.isFraud()).isTrue();
    assertThat(result.evaluationStatus()).isEqualTo("PARTIAL");
  }

  @Test
  void failuresAfterWritingHistoryRollbackHistoryResultAndWatermarkTogether() {
    RiskEvaluationRepository failing = spy(repository);
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("simulated write failure");
            })
        .when(failing)
        .save(any(), any(), any(), any(), any(), any());
    service = new TransactionEvaluationService(failing, new RiskScoreEngine(), mapper);
    assertThatThrownBy(() -> evaluate(request("failed", T, "A", "B", 100, null)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_transfer_history", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT last_ingest_order FROM risk_ingest_state", Long.class))
        .isZero();
  }

  @Test
  void rejectsSelfTransferInvalidPostingAndFutureTime() {
    assertThatThrownBy(() -> evaluate(request("self", T, "A", "A", 100, null)))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> evaluate(request("bad-posting", T, "A", "B", 100, T.minusSeconds(1))))
        .isInstanceOf(BusinessException.class);
    Instant future = Instant.now().plusSeconds(86400);
    assertThatThrownBy(() -> evaluate(request("future", future, "A", "B", 100, null)))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> evaluate(request("future-posting", T, "A", "B", 100, future)))
        .isInstanceOf(BusinessException.class);
  }

  private void certify(Instant from, Instant through) {
    jdbc.update(
        """
        INSERT INTO risk_account_coverage
          (account_id, outbound_from, outbound_through, inbound_posted_from, inbound_posted_through, posting_complete)
        VALUES ('A', ?, ?, ?, ?, TRUE)
        """,
        java.sql.Timestamp.from(from),
        java.sql.Timestamp.from(through),
        java.sql.Timestamp.from(from),
        java.sql.Timestamp.from(through));
  }

  private Result evaluate(EvaluationRequest request) {
    return transaction.execute(status -> service.evaluate(request));
  }

  private EvaluationRequest request(
      String ref, Instant time, String from, String to, long amount, Instant posted) {
    return new EvaluationRequest(
        ref,
        from,
        to,
        BigDecimal.valueOf(amount),
        "VND",
        time.atOffset(ZoneOffset.UTC),
        posted == null ? null : posted.atOffset(ZoneOffset.UTC));
  }
}
