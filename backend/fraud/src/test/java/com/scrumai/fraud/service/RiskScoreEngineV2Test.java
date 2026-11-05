package com.scrumai.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scrumai.fraud.service.RiskModels.Context;
import com.scrumai.fraud.service.RiskModels.Coverage;
import com.scrumai.fraud.service.RiskModels.Evaluation;
import com.scrumai.fraud.service.RiskModels.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RiskScoreEngineV2Test {
  private static final Instant T = Instant.parse("2026-10-01T05:00:00Z");
  private final RiskScoreEngine engine = new RiskScoreEngine(RiskScoreEngine.V2);

  @Test
  void knownReceiverAmountSpikeCanIndependentlyReachThreshold() {
    var evaluated =
        engine.evaluate(tx("now", "A", "known", 801, T, null), context(baseline(), List.of()));
    assertThat(evaluated.result().isFraud()).isTrue();
    assertThat(evaluated.result().riskScore()).isEqualTo(60);
    assertThat(evaluated.result().ruleVersion()).isEqualTo(RiskScoreEngine.V2);
    assertThat(
            engine
                .evaluate(
                    tx("boundary", "A", "known", 800, T, null), context(baseline(), List.of()))
                .result()
                .isFraud())
        .isFalse();
  }

  @Test
  void newRecipientStillSuppressesAmountInsteadOfDoubleCounting() {
    var evaluated = evaluate(baseline(), List.of(), 801);
    assertThat(evaluated.result().riskScore()).isEqualTo(65);
    assertThat(evaluated.result().triggeredRules())
        .anySatisfy(
            hit -> {
              assertThat(hit.ruleCode()).isEqualTo("AMOUNT_SPIKE");
              assertThat(hit.scoreContribution()).isZero();
              assertThat(hit.suppressedBy()).isEqualTo("NEW_BENEFICIARY_LARGE");
            });
  }

  @Test
  void threeTransfersInTwoMinutesDetectBurstWithoutFutureTransactions() {
    var history = new ArrayList<>(baseline());
    for (int age : new int[] {30, 60})
      history.add(tx("burst" + age, "A", "B", 100, T.minusSeconds(age), null));
    assertThat(evaluate(history, List.of(), 100).result().riskScore()).isEqualTo(65);
    history.removeLast();
    history.add(tx("future", "A", "B", 100, T.plusSeconds(1), null));
    history.add(tx("boundary", "A", "B", 100, T.minusSeconds(120), null));
    assertThat(evaluate(history, List.of(), 100).result().riskScore()).isZero();
  }

  @ParameterizedTest
  @CsvSource({"240,true", "480,true", "239,false", "481,false"})
  void detectsThreeEqualRegularTransfersOnlyInsideGapBounds(int gap, boolean expected) {
    var history = new ArrayList<>(baseline());
    history.add(tx("split1", "A", "known", 100, T.minusSeconds(2L * gap), null));
    history.add(tx("split2", "A", "known", 100, T.minusSeconds(gap), null));
    var evaluated =
        engine.evaluate(tx("now", "A", "known", 100, T, null), context(history, List.of()));
    assertThat(evaluated.result().isFraud()).isEqualTo(expected);
  }

  @Test
  void splitMembersDoNotCertifyPriorReceiverRelationship() {
    var history = new ArrayList<>(baseline());
    history.add(tx("split1", "A", "new", 100, T.minusSeconds(720), null));
    history.add(tx("split2", "A", "new", 100, T.minusSeconds(360), null));
    var evaluated =
        engine.evaluate(tx("now", "A", "new", 100, T, null), context(history, List.of()));
    assertThat(evaluated.result().triggeredRules())
        .noneMatch(hit -> hit.ruleCode().equals("STRUCTURED_SPLIT"));
  }

  @Test
  void ordinaryAmountPassThroughIsNotStandaloneFraud() {
    var incoming = tx("in", "C", "A", 100, T.minusSeconds(100), T.minusSeconds(90));
    assertThat(
            new RiskScoreEngine()
                .evaluate(tx("now", "A", "B", 98, T, null), context(baseline(), List.of(incoming)))
                .result()
                .isFraud())
        .isTrue();
    var evaluated = evaluate(baseline(), List.of(incoming), 98);
    assertThat(evaluated.result().isFraud()).isFalse();
    assertThat(evaluated.result().triggeredRules()).isEmpty();
  }

  @Test
  void flowRequiresBaselineAndNeverAssumesMissingPosting() {
    var incoming = tx("in", "C", "A", 1000, T.minusSeconds(100), T.minusSeconds(90));
    var evaluated = evaluate(List.of(), List.of(incoming), 980);
    assertThat(evaluated.result().isFraud()).isNull();
    assertThat(evaluated.result().unevaluatedRules())
        .anyMatch(rule -> rule.ruleCode().equals("RAPID_PASS_THROUGH"));
    var base = context(baseline(), List.of(incoming));
    var unknown = new Context(base.outbound(), base.inbound(), true, base.coverage(), 20L, T);
    assertThat(engine.evaluate(tx("now", "A", "B", 100, T, null), unknown).result().isFraud())
        .isNull();
  }

  @Test
  void multiFlowIncludesEarlierCreditsButRequiresAllSourcesAndConservation() {
    var inbound =
        IntStream.range(0, 5)
            .mapToObj(
                i ->
                    tx(
                        "in" + i,
                        "source" + i,
                        "A",
                        400,
                        T.minusSeconds(1200),
                        T.minusSeconds(1000 - i * 60L)))
            .toList();
    var history = new ArrayList<>(baseline());
    history.add(tx("first-out", "A", "D", 900, T.minusSeconds(300), null));
    assertThat(evaluate(history, inbound, 1000).result().triggeredRules())
        .anyMatch(hit -> hit.ruleCode().equals("MULTI_ACCOUNT_FLOW"));
    assertThat(
            new RiskScoreEngine()
                .evaluate(tx("now", "A", "B", 1000, T, null), context(history, inbound))
                .result()
                .triggeredRules())
        .noneMatch(hit -> hit.ruleCode().equals("MULTI_ACCOUNT_FLOW"));
    assertThat(evaluate(history, inbound.subList(1, 5), 1000).result().triggeredRules())
        .noneMatch(hit -> hit.ruleCode().equals("MULTI_ACCOUNT_FLOW"));
    assertThat(evaluate(history, inbound, 1200).result().triggeredRules())
        .noneMatch(hit -> hit.ruleCode().equals("MULTI_ACCOUNT_FLOW"));
  }

  @Test
  void newWindowRequiresThirtyMinuteCoverageAndRejectsFutureCredits() {
    var incoming = tx("future", "C", "A", 100, T.plusSeconds(1), T.plusSeconds(2));
    assertThat(evaluate(baseline(), List.of(incoming), 100).result().triggeredRules()).isEmpty();
    var covered =
        new Coverage("A", null, T.minusSeconds(40L * 86400), T, T.minusSeconds(600), T, true);
    var evaluated =
        engine.evaluate(
            tx("now", "A", "B", 100, T, null),
            new Context(baseline(), List.of(), false, covered, 20L, T));
    assertThat(evaluated.result().unevaluatedRules())
        .anyMatch(rule -> rule.ruleCode().equals("MULTI_ACCOUNT_FLOW"));
  }

  @Test
  void versionsHaveDistinctAuditIdentitiesAndUnknownVersionFailsClosed() {
    var current = tx("now", "A", "B", 1000, T, null);
    var v1 = new RiskScoreEngine().evaluate(current, context(baseline(), List.of()));
    var v2 = engine.evaluate(current, context(baseline(), List.of()));
    assertThat(v2.rules().getFirst().riskEventId())
        .isNotEqualTo(v1.rules().getFirst().riskEventId());
    assertThatThrownBy(() -> new RiskScoreEngine("typo"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private Evaluation evaluate(List<Transfer> outbound, List<Transfer> inbound, long amount) {
    return engine.evaluate(tx("now", "A", "B", amount, T, null), context(outbound, inbound));
  }

  private Context context(List<Transfer> outbound, List<Transfer> inbound) {
    return new Context(
        outbound,
        inbound,
        false,
        new Coverage(
            "A", null, T.minusSeconds(40L * 86400), T, T.minusSeconds(40L * 86400), T, true),
        20L,
        T);
  }

  private List<Transfer> baseline() {
    return IntStream.range(0, 20)
        .mapToObj(i -> tx("base" + i, "A", "known", 100, T.minusSeconds(86400 + i), null))
        .toList();
  }

  private Transfer tx(
      String ref, String from, String to, long amount, Instant time, Instant posting) {
    return new Transfer(ref, from, to, BigDecimal.valueOf(amount), "VND", time, posting);
  }
}
