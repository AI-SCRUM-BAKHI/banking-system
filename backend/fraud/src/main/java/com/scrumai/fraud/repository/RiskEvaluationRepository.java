package com.scrumai.fraud.repository;

import com.scrumai.fraud.service.RiskModels.Context;
import com.scrumai.fraud.service.RiskModels.Coverage;
import com.scrumai.fraud.service.RiskModels.Transfer;
import com.scrumai.fraud.service.RiskScoreEngine;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class RiskEvaluationRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public RiskEvaluationRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public record State(Instant watermark, long lastIngestOrder) {}

  public record Saved(String requestJson, String resultJson) {}

  public State lock() {
    // A database row lock serializes the global stream across JVMs as well as threads.
    return jdbc.queryForObject(
        "SELECT last_transaction_time_exact, last_ingest_order FROM risk_ingest_state WHERE id = 1 FOR UPDATE",
        (rs, row) ->
            new State(
                rs.getString("last_transaction_time_exact") == null
                    ? null
                    : Instant.parse(rs.getString("last_transaction_time_exact")),
                rs.getLong("last_ingest_order")));
  }

  public Optional<Saved> find(String ref) {
    return jdbc
        .query(
            "SELECT request_json, result_json FROM risk_transfer_history WHERE transaction_ref = ? ORDER BY ingest_order DESC LIMIT 1",
            (rs, row) -> new Saved(rs.getString("request_json"), rs.getString("result_json")),
            ref)
        .stream()
        .findFirst();
  }

  public Context context(Transfer current, State state) {
    Instant t = current.transactionTime();
    String account = current.senderAccountId();
    List<Coverage> coverage =
        jdbc.query(
            "SELECT * FROM risk_account_coverage WHERE account_id = ?",
            (rs, row) ->
                new Coverage(
                    account,
                    instant(rs, "opened_at"),
                    instant(rs, "outbound_from"),
                    instant(rs, "outbound_through"),
                    instant(rs, "inbound_posted_from"),
                    instant(rs, "inbound_posted_through"),
                    rs.getBoolean("posting_complete")),
            account);
    // Split needs thirty days before its first member, which may be one hour before t.
    List<Transfer> outbound =
        jdbc.query(
            """
        SELECT * FROM (
          SELECT h.*,
            row_number() OVER (PARTITION BY transaction_ref ORDER BY ingest_order DESC) AS evaluation_rank,
            min(ingest_order) OVER (PARTITION BY transaction_ref) AS first_ingest_order
          FROM risk_transfer_history h
          WHERE sender_account_id = ? AND transaction_time >= ? AND transaction_time <= ?
        ) history WHERE evaluation_rank = 1 ORDER BY first_ingest_order
        """,
            (rs, row) -> transfer(rs),
            account,
            Timestamp.from(t.minusSeconds(30L * 86400 + 3600).minusSeconds(1)),
            Timestamp.from(t.plusSeconds(1)));
    // Query by posting time: a long-delayed inbound may have an old transaction time.
    List<Transfer> inbound =
        jdbc.query(
            """
        SELECT * FROM (
          SELECT h.*,
            row_number() OVER (PARTITION BY transaction_ref ORDER BY ingest_order DESC) AS evaluation_rank,
            min(ingest_order) OVER (PARTITION BY transaction_ref) AS first_ingest_order
          FROM risk_transfer_history h
          WHERE receiver_account_id = ? AND posted_at >= ? AND posted_at < ?
        ) history WHERE evaluation_rank = 1 ORDER BY first_ingest_order
        """,
            (rs, row) -> transfer(rs),
            account,
            Timestamp.from(t.minusSeconds(RiskScoreEngine.FLOW_CONTEXT_SECONDS + 1)),
            Timestamp.from(t.plusSeconds(1)));
    boolean unknown =
        jdbc
            .query(
                """
        SELECT * FROM risk_transfer_history
        WHERE receiver_account_id = ? AND posted_at IS NULL AND transaction_time <= ?
        """,
                (rs, row) -> transfer(rs),
                account,
                Timestamp.from(t.plusSeconds(1)))
            .stream()
            .anyMatch(h -> h.transactionTime().isBefore(t));
    return new Context(
        outbound,
        inbound,
        unknown,
        coverage.isEmpty() ? null : coverage.getFirst(),
        state.lastIngestOrder(),
        state.watermark());
  }

  public void save(
      Transfer tx,
      State state,
      Instant evaluatedAt,
      String requestJson,
      String resultJson,
      String auditJson) {
    long order = state.lastIngestOrder() + 1;
    jdbc.update(
        """
        INSERT INTO risk_transfer_history
          (ingest_order, transaction_ref, sender_account_id, receiver_account_id, amount_vnd,
           currency, transaction_time, posted_at, evaluated_at, request_json, result_json, audit_json)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        order,
        tx.transactionRef(),
        tx.senderAccountId(),
        tx.receiverAccountId(),
        tx.amountVnd(),
        tx.currency(),
        Timestamp.from(tx.transactionTime()),
        tx.postedAt() == null ? null : Timestamp.from(tx.postedAt()),
        Timestamp.from(evaluatedAt),
        requestJson,
        resultJson,
        auditJson);
    Instant watermark =
        state.watermark() == null || tx.transactionTime().isAfter(state.watermark())
            ? tx.transactionTime()
            : state.watermark();
    jdbc.update(
        "UPDATE risk_ingest_state SET last_transaction_time = ?, last_transaction_time_exact = ?, last_ingest_order = ? WHERE id = 1",
        Timestamp.from(watermark),
        watermark.toString(),
        order);
  }

  private Transfer transfer(ResultSet rs) throws SQLException {
    // PostgreSQL timestamps have microsecond precision. Hydrate canonical JSON to
    // retain nanoseconds; coarse indexed SQL bounds are filtered exactly by the engine.
    return mapper.readValue(rs.getString("request_json"), Transfer.class);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
