package com.scrumai.fraud.service;

import com.scrumai.fraud.enums.ReviewStatus;
import com.scrumai.fraud.enums.RiskLevel;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IpIntelligenceService {
  private final JdbcTemplate jdbc;

  public IpIntelligenceService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record IpCase(
      UUID id,
      String transactionRef,
      String fromAccountMasked,
      String toAccountMasked,
      BigDecimal amount,
      String currency,
      Instant transactionTime,
      BigDecimal riskScore,
      RiskLevel riskLevel,
      ReviewStatus reviewStatus,
      Instant createdAt,
      String ipAddress,
      boolean sourceFraud) {}

  public record Group(
      String ip,
      long count,
      long sourceFraud,
      long engineFraud,
      long confirmed,
      long highRisk,
      int accounts,
      List<String> accountLabels,
      BigDecimal amount,
      List<IpCase> records) {}

  public record Insights(
      long totalTransactions,
      long transactionsWithIp,
      long sourceFraudTransactions,
      List<Group> groups) {}

  record Observation(
      String ip,
      String sender,
      String receiver,
      BigDecimal amount,
      boolean sourceFraud,
      boolean engineFraud,
      IpCase alert) {}

  @Transactional(readOnly = true)
  public Insights load() {
    return summarize(
        jdbc.query(
            """
            SELECT h.sender_account_id, h.receiver_account_id, h.amount_vnd,
              h.audit_json::jsonb #>> '{sourceTransaction,ip_address}' AS source_ip,
              h.audit_json::jsonb #>> '{sourceScenarioLabel,is_fraud}' AS source_label,
              h.result_json::jsonb ->> 'decision' AS engine_decision, f.*
            FROM risk_transfer_history h
            LEFT JOIN fraud_transactions f ON f.transaction_ref = h.transaction_ref
            ORDER BY h.ingest_order
            """,
            this::readObservation));
  }

  private Observation readObservation(ResultSet rs, int rowNumber) throws SQLException {
    String ip = rs.getString("source_ip");
    boolean labelled = "1".equals(rs.getString("source_label"));
    UUID id = rs.getObject("id", UUID.class);
    IpCase alert =
        id == null
            ? null
            : new IpCase(
                id,
                rs.getString("transaction_ref"),
                rs.getString("from_account_masked"),
                rs.getString("to_account_masked"),
                rs.getBigDecimal("amount"),
                rs.getString("currency").trim(),
                rs.getTimestamp("transaction_time").toInstant(),
                rs.getBigDecimal("risk_score"),
                RiskLevel.valueOf(rs.getString("risk_level")),
                ReviewStatus.valueOf(rs.getString("review_status")),
                rs.getTimestamp("created_at").toInstant(),
                ip,
                labelled);
    return new Observation(
        ip,
        rs.getString("sender_account_id"),
        rs.getString("receiver_account_id"),
        rs.getBigDecimal("amount_vnd"),
        labelled,
        "FRAUD".equals(rs.getString("engine_decision")),
        alert);
  }

  static Insights summarize(List<Observation> observations) {
    Map<String, Accumulator> groups = new LinkedHashMap<>();
    long withIp = 0;
    long labelled = 0;
    for (Observation observation : observations) {
      if (observation.ip() == null || observation.ip().isBlank()) {
        continue;
      }
      withIp++;
      if (observation.sourceFraud()) {
        labelled++;
      }
      groups.computeIfAbsent(observation.ip().trim(), Accumulator::new).add(observation);
    }
    List<Group> result =
        groups.values().stream()
            .map(Accumulator::result)
            .sorted(
                Comparator.comparingLong(Group::sourceFraud)
                    .reversed()
                    .thenComparing(Comparator.comparingLong(Group::confirmed).reversed())
                    .thenComparing(Comparator.comparingLong(Group::count).reversed())
                    .thenComparing(Group::ip))
            .toList();
    return new Insights(observations.size(), withIp, labelled, result);
  }

  private static final class Accumulator {
    private final String ip;
    private final Set<String> accounts = new LinkedHashSet<>();
    private final List<IpCase> records = new ArrayList<>();
    private long count;
    private long sourceFraud;
    private long engineFraud;
    private long confirmed;
    private long highRisk;
    private BigDecimal amount = BigDecimal.ZERO;

    private Accumulator(String ip) {
      this.ip = ip;
    }

    private void add(Observation observation) {
      count++;
      sourceFraud += observation.sourceFraud() ? 1 : 0;
      engineFraud += observation.engineFraud() ? 1 : 0;
      amount = amount.add(observation.amount());
      accounts.add(observation.sender());
      accounts.add(observation.receiver());
      if (observation.alert() != null) {
        IpCase alert = observation.alert();
        records.add(alert);
        confirmed += alert.reviewStatus() == ReviewStatus.CONFIRMED_FRAUD ? 1 : 0;
        highRisk +=
            alert.riskLevel() == RiskLevel.HIGH || alert.riskLevel() == RiskLevel.CRITICAL ? 1 : 0;
      }
    }

    private Group result() {
      List<String> labels =
          accounts.stream()
              .limit(6)
              .map(a -> "**** " + a.substring(Math.max(0, a.length() - 4)))
              .toList();
      List<IpCase> sorted =
          records.stream()
              .sorted(
                  Comparator.comparing(IpCase::sourceFraud)
                      .reversed()
                      .thenComparing(IpCase::transactionTime, Comparator.reverseOrder()))
              .toList();
      return new Group(
          ip,
          count,
          sourceFraud,
          engineFraud,
          confirmed,
          highRisk,
          accounts.size(),
          labels,
          amount,
          sorted);
    }
  }
}
