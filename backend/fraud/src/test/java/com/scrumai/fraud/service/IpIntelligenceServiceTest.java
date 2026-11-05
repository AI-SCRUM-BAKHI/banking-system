package com.scrumai.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class IpIntelligenceServiceTest {
  @Test
  void groupsOriginalSourceIpsAndKeepsDatasetLabelsSeparateFromReviewAndPrediction() {
    var result =
        IpIntelligenceService.summarize(
            List.of(
                new IpIntelligenceService.Observation(
                    "192.0.2.54", "A", "B", BigDecimal.TEN, true, false, null),
                new IpIntelligenceService.Observation(
                    "192.0.2.54", "A", "C", BigDecimal.TEN, true, true, null),
                new IpIntelligenceService.Observation(
                    "192.0.2.55", "D", "E", BigDecimal.ONE, false, false, null),
                new IpIntelligenceService.Observation(
                    null, "F", "G", BigDecimal.ONE, false, false, null)));
    assertThat(result.totalTransactions()).isEqualTo(4);
    assertThat(result.transactionsWithIp()).isEqualTo(3);
    assertThat(result.sourceFraudTransactions()).isEqualTo(2);
    assertThat(result.groups()).hasSize(2);
    var shared = result.groups().getFirst();
    assertThat(shared.ip()).isEqualTo("192.0.2.54");
    assertThat(shared.count()).isEqualTo(2);
    assertThat(shared.sourceFraud()).isEqualTo(2);
    assertThat(shared.engineFraud()).isEqualTo(1);
    assertThat(shared.confirmed()).isZero();
    assertThat(shared.accounts()).isEqualTo(3);
    assertThat(shared.amount()).isEqualByComparingTo("20");
  }

  @Test
  void reviewConfirmationDoesNotRewriteTheSourceLabelOrEngineDecision() {
    var alert =
        new IpIntelligenceService.IpCase(
            java.util.UUID.randomUUID(),
            "REF",
            "**** 0001",
            "**** 0002",
            BigDecimal.TEN,
            "VND",
            java.time.Instant.EPOCH,
            BigDecimal.valueOf(80),
            com.scrumai.fraud.enums.RiskLevel.CRITICAL,
            com.scrumai.fraud.enums.ReviewStatus.CONFIRMED_FRAUD,
            java.time.Instant.EPOCH,
            "192.0.2.54",
            false);
    var group =
        IpIntelligenceService.summarize(
                List.of(
                    new IpIntelligenceService.Observation(
                        "192.0.2.54", "A", "B", BigDecimal.TEN, false, false, alert)))
            .groups()
            .getFirst();
    assertThat(group.confirmed()).isEqualTo(1);
    assertThat(group.sourceFraud()).isZero();
    assertThat(group.engineFraud()).isZero();
    assertThat(group.highRisk()).isEqualTo(1);
    assertThat(group.records()).containsExactly(alert);
  }
}
