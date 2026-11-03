package com.scrumai.fraud.dto;

import com.scrumai.fraud.enums.ReviewStatus;
import com.scrumai.fraud.enums.RiskLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FraudResponses {

  private FraudResponses() {}

  public record Created(
      UUID id, String transactionRef, ReviewStatus reviewStatus, Instant createdAt) {}

  public record Account(String ref, String masked, String customerName) {}

  public record RuleHit(
      UUID id,
      String ruleCode,
      String ruleName,
      BigDecimal scoreContribution,
      String explanation) {}

  public record SummaryItem(
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
      Instant createdAt) {}

  public record Detail(
      UUID id,
      String transactionRef,
      Account fromAccount,
      Account toAccount,
      BigDecimal amount,
      String currency,
      Instant transactionTime,
      BigDecimal riskScore,
      RiskLevel riskLevel,
      String fraudReason,
      ReviewStatus reviewStatus,
      Instant createdAt,
      List<RuleHit> ruleHits) {}

  public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

  public record GraphNode(String id, String masked, String customerName) {}

  public record GraphEdge(
      UUID id,
      String transactionRef,
      String fromAccountRef,
      String toAccountRef,
      BigDecimal amount,
      String currency,
      Instant transactionTime,
      int hopLevel,
      boolean root) {}

  public record Graph(List<GraphNode> nodes, List<GraphEdge> edges) {}

  public record ReviewResult(UUID id, ReviewStatus reviewStatus) {}

  public record Dashboard(
      long total,
      long newCases,
      long reviewingCases,
      long confirmedFraud,
      long falsePositives,
      long highRisk,
      long criticalRisk) {}
}
