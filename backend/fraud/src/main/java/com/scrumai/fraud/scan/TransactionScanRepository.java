package com.scrumai.fraud.scan;

import com.scrumai.fraud.dto.FraudRequests.RuleHit;
import com.scrumai.fraud.repository.RiskEvaluationRepository;
import com.scrumai.fraud.service.RiskModels.Context;
import com.scrumai.fraud.service.RiskModels.Coverage;
import com.scrumai.fraud.service.RiskModels.Evaluation;
import com.scrumai.fraud.service.RiskModels.Transfer;
import com.scrumai.fraud.service.RiskScoreEngine;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class TransactionScanRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final RiskEvaluationRepository evaluations;

  public TransactionScanRepository(
      JdbcTemplate jdbc, ObjectMapper mapper, RiskEvaluationRepository evaluations) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.evaluations = evaluations;
  }

  public List<String> candidates(int limit) {
    return jdbc.query(
        """
        SELECT transaction_ref FROM transactions
        WHERE processing_status = 'PENDING'
           OR (processing_status = 'RETRY' AND next_attempt_at <= CURRENT_TIMESTAMP)
        ORDER BY transaction_time, transaction_ref LIMIT ?
        """,
        (rs, row) -> rs.getString(1),
        limit);
  }

  public Optional<SourceTransaction> lock(String ref) {
    return jdbc
        .query(
            """
        SELECT * FROM transactions WHERE transaction_ref = ?
          AND (processing_status = 'PENDING'
            OR (processing_status = 'RETRY' AND next_attempt_at <= CURRENT_TIMESTAMP))
        FOR UPDATE
        """,
            (rs, row) -> source(rs),
            ref)
        .stream()
        .findFirst();
  }

  public Context context(SourceTransaction tx) {
    Instant t = tx.transactionTime();
    List<Transfer> outbound =
        jdbc.query(
            """
        SELECT * FROM transactions WHERE from_account_ref = ?
          AND transaction_ref <> ? AND transaction_time >= ?
          AND (transaction_time < ? OR (transaction_time = ? AND transaction_ref < ?))
        ORDER BY transaction_time, transaction_ref
        """,
            (rs, row) -> contextTransfer(rs),
            tx.fromAccountRef(),
            tx.transactionRef(),
            Timestamp.from(t.minusSeconds(30L * 86400 + 3600)),
            Timestamp.from(t),
            Timestamp.from(t),
            tx.transactionRef());
    List<Transfer> inbound =
        jdbc.query(
            """
        SELECT * FROM transactions WHERE to_account_ref = ? AND transaction_ref <> ?
          AND posted_at >= ? AND posted_at < ? AND transaction_time <= ?
        ORDER BY transaction_time, transaction_ref
        """,
            (rs, row) -> contextTransfer(rs),
            tx.fromAccountRef(),
            tx.transactionRef(),
            Timestamp.from(t.minusSeconds(RiskScoreEngine.FLOW_CONTEXT_SECONDS)),
            Timestamp.from(t),
            Timestamp.from(t));
    Boolean unknown =
        jdbc.queryForObject(
            """
        SELECT EXISTS (SELECT 1 FROM transactions WHERE to_account_ref = ?
          AND transaction_ref <> ? AND posted_at IS NULL AND transaction_time < ?)
        """,
            Boolean.class,
            tx.fromAccountRef(),
            tx.transactionRef(),
            Timestamp.from(t));
    List<Coverage> coverage =
        jdbc.query(
            """
        SELECT * FROM risk_account_coverage WHERE account_id = ?
        """,
            (rs, row) ->
                new Coverage(
                    tx.fromAccountRef(),
                    instant(rs, "opened_at"),
                    instant(rs, "outbound_from"),
                    instant(rs, "outbound_through"),
                    instant(rs, "inbound_posted_from"),
                    instant(rs, "inbound_posted_through"),
                    rs.getBoolean("posting_complete")),
            tx.fromAccountRef());
    return new Context(
        outbound,
        inbound,
        Boolean.TRUE.equals(unknown),
        coverage.isEmpty() ? null : coverage.getFirst(),
        null,
        null);
  }

  public void save(
      SourceTransaction tx,
      Evaluation evaluation,
      Instant now,
      List<RuleHit> hits,
      String reason,
      String audit) {
    var result = evaluation.result();
    evaluations.save(
        tx.transfer(),
        evaluations.lock(),
        now,
        mapper.writeValueAsString(tx.transfer()),
        mapper.writeValueAsString(result),
        audit);
    jdbc.update(
        """
        UPDATE transactions SET risk_score = ?, risk_level = ?, fraud_reason = ?,
          rule_hits = CAST(? AS jsonb), transaction_chain = CAST(? AS jsonb),
          processing_status = 'COMPLETED', attempt_count = attempt_count + 1,
          next_attempt_at = NULL, evaluated_at = ?, evaluation_status = ?,
          evaluation_decision = ?, rule_version = ?, last_error = NULL
        WHERE transaction_ref = ?
        """,
        result.riskScore(),
        result.riskLevel().name(),
        reason,
        mapper.writeValueAsString(hits),
        mapper.writeValueAsString(List.of(tx.root())),
        Timestamp.from(now),
        result.evaluationStatus(),
        result.decision(),
        result.ruleVersion(),
        tx.transactionRef());
    if (Boolean.TRUE.equals(result.isFraud())) {
      createFraud(tx, evaluation, hits, reason);
    }
  }

  private void createFraud(
      SourceTransaction tx, Evaluation evaluation, List<RuleHit> hits, String reason) {
    // ON CONFLICT also handles a concurrent legacy API insert without aborting this transaction.
    List<UUID> inserted =
        jdbc.query(
            """
        INSERT INTO fraud_transactions
          (transaction_ref, from_account_ref, from_account_masked, from_customer_name,
           to_account_ref, to_account_masked, to_customer_name, amount, currency,
           transaction_time, risk_score, risk_level, fraud_reason)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (transaction_ref) DO NOTHING RETURNING id
        """,
            (rs, row) -> rs.getObject("id", UUID.class),
            tx.transactionRef(),
            tx.fromAccountRef(),
            tx.fromAccountMasked(),
            tx.fromCustomerName(),
            tx.toAccountRef(),
            tx.toAccountMasked(),
            tx.toCustomerName(),
            tx.amount(),
            tx.currency(),
            Timestamp.from(tx.transactionTime()),
            evaluation.result().riskScore(),
            evaluation.result().riskLevel().name(),
            reason);
    if (inserted.isEmpty()) {
      return;
    }
    UUID id = inserted.getFirst();
    for (RuleHit hit : hits) {
      jdbc.update(
          """
          INSERT INTO fraud_rule_hits
            (fraud_transaction_id, rule_code, rule_name, score_contribution, explanation)
          VALUES (?, ?, ?, ?, ?)
          """,
          id,
          hit.ruleCode(),
          hit.ruleName(),
          hit.scoreContribution(),
          hit.explanation());
    }
    jdbc.update(
        """
        INSERT INTO transaction_chain
          (root_fraud_transaction_id, transaction_ref, from_account_ref, from_account_masked,
           from_customer_name, to_account_ref, to_account_masked, to_customer_name,
           amount, currency, transaction_time, hop_level, is_root)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, TRUE)
        """,
        id,
        tx.transactionRef(),
        tx.fromAccountRef(),
        tx.fromAccountMasked(),
        tx.fromCustomerName(),
        tx.toAccountRef(),
        tx.toAccountMasked(),
        tx.toCustomerName(),
        tx.amount(),
        tx.currency(),
        Timestamp.from(tx.transactionTime()));
  }

  public String recordFailure(String ref, boolean temporary, String error, Instant now) {
    List<Integer> attempts =
        jdbc.query(
            """
        SELECT attempt_count FROM transactions WHERE transaction_ref = ?
          AND processing_status IN ('PENDING', 'RETRY') FOR UPDATE
        """,
            (rs, row) -> rs.getInt(1),
            ref);
    if (attempts.isEmpty()) {
      return "SKIPPED";
    }
    int attempt = attempts.getFirst() + 1;
    boolean retry = temporary && attempt < 4;
    long delay =
        switch (attempt) {
          case 1 -> 30;
          case 2 -> 120;
          default -> 600;
        };
    String status = retry ? "RETRY" : "FAILED";
    jdbc.update(
        """
        UPDATE transactions SET processing_status = ?, attempt_count = ?,
          next_attempt_at = ?, last_error = ? WHERE transaction_ref = ?
        """,
        status,
        attempt,
        retry ? Timestamp.from(now.plusSeconds(delay)) : null,
        error,
        ref);
    return status;
  }

  private static Transfer contextTransfer(ResultSet rs) throws SQLException {
    SourceTransaction tx = source(rs);
    if (!"VND".equals(tx.currency())
        || tx.amount().signum() <= 0
        || tx.amount().stripTrailingZeros().scale() > 0
        || tx.fromAccountRef().equals(tx.toAccountRef())
        || tx.postedAt() != null && tx.postedAt().isBefore(tx.transactionTime())) {
      throw new IllegalArgumentException(
          "Incompatible source history; repair before certifying coverage");
    }
    return tx.transfer();
  }

  private static SourceTransaction source(ResultSet rs) throws SQLException {
    return new SourceTransaction(
        rs.getString("transaction_ref"),
        rs.getString("from_account_ref"),
        rs.getString("from_account_masked"),
        rs.getString("from_customer_name"),
        rs.getString("to_account_ref"),
        rs.getString("to_account_masked"),
        rs.getString("to_customer_name"),
        rs.getBigDecimal("amount"),
        rs.getString("currency"),
        instant(rs, "transaction_time"),
        instant(rs, "posted_at"));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }
}
