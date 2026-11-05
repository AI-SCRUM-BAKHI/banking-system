package com.scrumai.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scrumai.fraud.repository.AiAnalysisRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AiAnalysisPersistenceTest {
  @Test
  void migrationAndRepositoryPreserveFirstAnalysisAndAuditMetadata() throws Exception {
    var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""));
    for (String name :
        new String[] {
          "V4__create_risk_evaluation_context.sql", "V5__create_fraud_ai_analysis.sql"
        }) {
      String migration =
          new ClassPathResource("db/migration/" + name)
              .getContentAsString(StandardCharsets.UTF_8)
              .replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE");
      for (String statement : migration.split(";")) {
        if (!statement.isBlank()) {
          jdbc.execute(statement);
        }
      }
    }
    jdbc.update(
        """
        INSERT INTO risk_transfer_history
          (ingest_order, transaction_ref, sender_account_id, receiver_account_id,
           amount_vnd, currency, transaction_time, evaluated_at, request_json,
           result_json, audit_json)
        VALUES (1, 'TX-1', 'A', 'B', 100, 'VND', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                '{}', '{"decision":"FRAUD"}', '{"rules":[]}')
        """);
    var repository = new AiAnalysisRepository(jdbc);
    assertThat(repository.find("TX-1")).isEmpty();
    assertThat(repository.snapshot("TX-1"))
        .get()
        .extracting(AiAnalysisRepository.Snapshot::resultJson)
        .isEqualTo("{\"decision\":\"FRAUD\"}");
    String hash = "a".repeat(64);
    repository.save("TX-1", "{\"first\":true}", hash, "test/model", "fraud-analysis-v1");
    repository.save("TX-1", "{\"replacement\":true}", "b".repeat(64), "other/model", "v2");
    assertThat(repository.find("TX-1")).contains("{\"first\":true}");
    assertThat(
            jdbc.queryForMap(
                "SELECT snapshot_hash, model, prompt_version FROM fraud_ai_analysis WHERE transaction_ref = 'TX-1'"))
        .containsEntry("snapshot_hash", hash)
        .containsEntry("model", "test/model")
        .containsEntry("prompt_version", "fraud-analysis-v1");
    assertThatThrownBy(() -> repository.save("UNKNOWN", "{}", hash, "test/model", "v1"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
