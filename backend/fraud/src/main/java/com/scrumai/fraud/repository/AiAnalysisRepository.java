package com.scrumai.fraud.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiAnalysisRepository {
  private final JdbcTemplate jdbc;

  public AiAnalysisRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Snapshot(String requestJson, String resultJson, String auditJson) {}

  public Optional<Snapshot> snapshot(String ref) {
    return jdbc
        .query(
            "SELECT request_json, result_json, audit_json FROM risk_transfer_history WHERE transaction_ref = ?",
            (rs, row) -> new Snapshot(rs.getString(1), rs.getString(2), rs.getString(3)),
            ref)
        .stream()
        .findFirst();
  }

  public Optional<String> find(String ref) {
    return jdbc
        .query(
            "SELECT analysis_json FROM fraud_ai_analysis WHERE transaction_ref = ?",
            (rs, row) -> rs.getString(1),
            ref)
        .stream()
        .findFirst();
  }

  public void save(String ref, String json, String hash, String model, String version) {
    if (snapshot(ref).isEmpty()) {
      throw new DataIntegrityViolationException(
          "Transaction " + ref + " does not exist in risk transfer history");
    }
    jdbc.update(
        """
        INSERT INTO fraud_ai_analysis
        (transaction_ref, analysis_json, snapshot_hash, model, prompt_version, created_at)
        VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
        """,
        ref,
        json,
        hash,
        model,
        version,
        Timestamp.from(Instant.now()));
  }
}
