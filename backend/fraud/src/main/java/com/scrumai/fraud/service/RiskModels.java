package com.scrumai.fraud.service;

import com.scrumai.fraud.enums.RiskLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RiskModels {
  private RiskModels() {}

  public record Transfer(
      String transactionRef,
      String senderAccountId,
      String receiverAccountId,
      BigDecimal amountVnd,
      String currency,
      Instant transactionTime,
      Instant postedAt) {}

  // Coverage is certified by the trusted data importer, never by evaluation callers.
  // Inbound coverage is explicitly in posting time, including posting completeness.
  public record Coverage(
      String accountId,
      Instant openedAt,
      Instant outboundFrom,
      Instant outboundThrough,
      Instant inboundPostedFrom,
      Instant inboundPostedThrough,
      boolean postingComplete) {
    public boolean outbound(Instant from, Instant through) {
      return covers(outboundFrom, outboundThrough, from, through);
    }

    public boolean inbound(Instant from, Instant through) {
      return postingComplete && covers(inboundPostedFrom, inboundPostedThrough, from, through);
    }

    private boolean covers(Instant start, Instant end, Instant from, Instant through) {
      Instant required = openedAt != null && openedAt.isAfter(from) ? openedAt : from;
      return start != null && end != null && !start.isAfter(required) && !end.isBefore(through);
    }
  }

  public record Context(
      List<Transfer> outbound,
      List<Transfer> inbound,
      boolean unknownPosting,
      Coverage coverage,
      Long lastIngestOrder,
      Instant watermark) {}

  public record TriggeredRule(String ruleCode, int scoreContribution, String suppressedBy) {}

  public record UnevaluatedRule(String ruleCode, String reason) {}

  public record Result(
      String transactionRef,
      Boolean isFraud,
      String decision,
      int riskScore,
      RiskLevel riskLevel,
      String evaluationStatus,
      int fraudThreshold,
      String ruleVersion,
      List<TriggeredRule> triggeredRules,
      List<UnevaluatedRule> unevaluatedRules) {}

  public record RuleAudit(
      String ruleCode,
      String status,
      String reason,
      Map<String, Object> features,
      List<String> evidence,
      String riskEventId) {}

  public record Evaluation(Result result, List<RuleAudit> rules) {}
}
