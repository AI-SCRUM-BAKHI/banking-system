package com.scrumai.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.scrumai.fraud.enums.RiskLevel;
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

class RiskScoreEngineTest {
  private static final Instant T = Instant.parse("2026-10-01T05:00:00Z");
  private final RiskScoreEngine engine = new RiskScoreEngine();

  @Test
  void missingContextIsPartialAndNeverFalse() {
    Evaluation evaluated =
        engine.evaluate(current(100), new Context(List.of(), List.of(), false, null, 0L, null));
    assertThat(evaluated.result().riskScore()).isZero();
    assertThat(evaluated.result().isFraud()).isNull();
    assertThat(evaluated.result().decision()).isEqualTo("INSUFFICIENT_DATA");
    assertThat(evaluated.result().unevaluatedRules()).hasSize(6);
    assertThat(evaluated.rules()).hasSize(7);
  }

  @ParameterizedTest
  @CsvSource({"01:59:59,0", "02:00:00,25", "03:59:59,25", "04:00:00,0"})
  void hourUsesVietnamTime(String local, int score) {
    Instant time = java.time.OffsetDateTime.parse("2026-10-01T" + local + "+07:00").toInstant();
    Transfer tx = tx("now", "A", "B", 100, time, time);
    Evaluation evaluated = engine.evaluate(tx, complete(baseline(time), List.of()));
    assertThat(evaluated.result().riskScore()).isEqualTo(score);
    assertThat(evaluated.result().isFraud()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"800,0", "801,50"})
  void exactAmountBoundaryAndSuppression(long amount, int score) {
    Evaluation evaluated = engine.evaluate(current(amount), complete(baseline(T), List.of()));
    assertThat(evaluated.result().riskScore()).isEqualTo(score);
    if (score > 0) {
      assertThat(evaluated.result().triggeredRules())
          .anySatisfy(
              r -> {
                assertThat(r.ruleCode()).isEqualTo("AMOUNT_SPIKE");
                assertThat(r.scoreContribution()).isZero();
                assertThat(r.suppressedBy()).isEqualTo("NEW_BENEFICIARY_LARGE");
              });
    }
  }

  @Test
  void evenMedianIsExactAndDoesNotRoundRatio() {
    List<Transfer> history = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      history.add(tx("h" + i, "A", "old", i < 10 ? 100 : 101, T.minusSeconds(86400 + i), null));
    }
    assertThat(engine.evaluate(current(804), complete(history, List.of())).result().riskScore())
        .isZero();
    assertThat(engine.evaluate(current(805), complete(history, List.of())).result().riskScore())
        .isEqualTo(50);
  }

  @Test
  void amountAndHourReachFraudThresholdExactly() {
    Instant night = Instant.parse("2026-10-01T19:00:00Z");
    List<Transfer> history = new ArrayList<>(baseline(night));
    history.add(tx("known", "A", "B", 100, night.minusSeconds(86400), null));
    Evaluation evaluated =
        engine.evaluate(tx("now", "A", "B", 900, night, null), complete(history, List.of()));
    assertThat(evaluated.result().riskScore()).isEqualTo(60);
    assertThat(evaluated.result().riskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(evaluated.result().isFraud()).isTrue();
  }

  @Test
  void requiresTwentySamplesAndIgnoresSameTimeForBaseline() {
    List<Transfer> history = new ArrayList<>(baseline(T).subList(0, 19));
    history.add(tx("same-time", "A", "B", 100, T, null));
    Evaluation evaluated = engine.evaluate(current(1000), complete(history, List.of()));
    assertThat(evaluated.result().unevaluatedRules())
        .extracting(RiskModels.UnevaluatedRule::ruleCode)
        .containsExactly("AMOUNT_SPIKE", "NEW_BENEFICIARY_LARGE");
  }

  @Test
  void receiverAtThirtyDayBoundaryIsKnownButSameTimestampIsNot() {
    List<Transfer> history = new ArrayList<>(baseline(T));
    history.add(tx("known", "A", "B", 100, T.minusSeconds(30L * 86400), null));
    assertThat(engine.evaluate(current(900), complete(history, List.of())).result().riskScore())
        .isEqualTo(35);
    history.removeLast();
    history.add(tx("same", "A", "B", 100, T, null));
    assertThat(engine.evaluate(current(900), complete(history, List.of())).result().riskScore())
        .isEqualTo(50);
  }

  @Test
  void frequencyIncludesCurrentAndSameTimeButExcludesLeftBoundary() {
    List<Transfer> history = new ArrayList<>(baseline(T));
    for (int i = 0; i < 10; i++) {
      history.add(tx("burst" + i, "A", "B", 100, T.minusSeconds(i), null));
    }
    history.add(tx("boundary", "A", "B", 100, T.minusSeconds(600), null));
    Evaluation eleven = engine.evaluate(current(100), complete(history, List.of()));
    assertThat(eleven.result().riskScore()).isZero();
    history.add(tx("twelfth", "A", "B", 100, T, null));
    assertThat(engine.evaluate(current(100), complete(history, List.of())).result().riskScore())
        .isEqualTo(45);
    assertThat(eleven.result().riskScore()).isZero();
  }

  @ParameterizedTest
  @CsvSource({"300,55", "420,55", "299,0", "421,0"})
  void structuredSplitGapBoundaries(int gap, int expected) {
    List<Transfer> history = new ArrayList<>(baseline(T));
    history.add(tx("old-receiver", "A", "B", 100, T.minusSeconds(86400), null));
    for (int i = 8; i >= 1; i--) {
      history.add(tx("split" + i, "A", "B", 100, T.minusSeconds((long) i * gap), null));
    }
    assertThat(engine.evaluate(current(100), complete(history, List.of())).result().riskScore())
        .isEqualTo(expected);
  }

  @Test
  void splitDoesNotUseMembersAsReceiverHistoryAndDoesNotSearchSubsets() {
    List<Transfer> history = new ArrayList<>(baseline(T));
    for (int i = 8; i >= 1; i--) {
      history.add(tx("split" + i, "A", "B", 100, T.minusSeconds(i * 360L), null));
    }
    assertThat(engine.evaluate(current(100), complete(history, List.of())).result().riskScore())
        .isZero();
    history.addFirst(tx("old-receiver", "A", "B", 100, T.minusSeconds(86400), null));
    history.add(tx("interference", "A", "B", 101, T.minusSeconds(1), null));
    assertThat(engine.evaluate(current(100), complete(history, List.of())).result().riskScore())
        .isZero();
  }

  @Test
  void splitRequiresCoverageBeforeFirstMember() {
    List<Transfer> history = new ArrayList<>();
    for (int i = 8; i >= 1; i--) {
      history.add(tx("split" + i, "A", "B", 100, T.minusSeconds(i * 360L), null));
    }
    Coverage coverage =
        new Coverage("A", null, T.minusSeconds(3600), T, T.minusSeconds(600), T, true);
    Evaluation evaluated =
        engine.evaluate(current(100), new Context(history, List.of(), false, coverage, 8L, T));
    assertThat(evaluated.result().unevaluatedRules())
        .extracting(RiskModels.UnevaluatedRule::ruleCode)
        .contains("STRUCTURED_SPLIT");
  }

  @Test
  void frequencyAndSplitContributeOnlyTheLargerTemporalWeight() {
    List<Transfer> history = new ArrayList<>(baseline(T));
    history.add(tx("known", "A", "B", 100, T.minusSeconds(86400), null));
    for (int i = 8; i >= 1; i--) {
      history.add(tx("split" + i, "A", "B", 100, T.minusSeconds(i * 360L), null));
    }
    for (int i = 1; i <= 10; i++) {
      history.add(tx("burst" + i, "A", "other", 100, T.minusSeconds(i), null));
    }
    Evaluation evaluated = engine.evaluate(current(100), complete(history, List.of()));
    assertThat(evaluated.result().riskScore()).isEqualTo(55);
    assertThat(evaluated.result().triggeredRules())
        .anySatisfy(
            r -> {
              assertThat(r.ruleCode()).isEqualTo("FREQUENCY_SPIKE");
              assertThat(r.scoreContribution()).isZero();
              assertThat(r.suppressedBy()).isEqualTo("STRUCTURED_SPLIT");
            });
  }

  @ParameterizedTest
  @CsvSource({"60,95,65", "120,100,65", "59,100,0", "121,100,0", "0,100,0", "60,94,0", "60,101,0"})
  void rapidPostingDelayAndRatioBoundaries(int delay, long amount, int expected) {
    Transfer incoming = tx("incoming", "C", "A", 100, T.minusSeconds(10000), T.minusSeconds(delay));
    Evaluation evaluated =
        engine.evaluate(current(amount), complete(baseline(T), List.of(incoming)));
    assertThat(evaluated.result().riskScore()).isEqualTo(expected);
  }

  @Test
  void rapidChoosesLatestAndLexicalTieWithoutTryingAnotherInbound() {
    Transfer valid = tx("z", "C", "A", 100, T.minusSeconds(150), T.minusSeconds(90));
    Transfer tooRecent = tx("recent", "D", "A", 100, T.minusSeconds(100), T.minusSeconds(30));
    Transfer lexical = tx("a", "B", "A", 100, T.minusSeconds(150), T.minusSeconds(90));
    assertThat(
            engine
                .evaluate(current(100), complete(baseline(T), List.of(valid, tooRecent)))
                .result()
                .riskScore())
        .isZero();
    assertThat(
            engine
                .evaluate(current(100), complete(baseline(T), List.of(valid, lexical)))
                .result()
                .riskScore())
        .isZero();
  }

  @Test
  void missingBaselineCanStillPredictFraudButMissingPostingCannotMatchFlow() {
    Transfer incoming = tx("incoming", "C", "A", 100, T.minusSeconds(90), T.minusSeconds(60));
    Context ctx = complete(List.of(), List.of(incoming));
    Evaluation evaluated = engine.evaluate(current(95), ctx);
    assertThat(evaluated.result().riskScore()).isEqualTo(65);
    assertThat(evaluated.result().evaluationStatus()).isEqualTo("PARTIAL");
    assertThat(evaluated.result().isFraud()).isTrue();
    Context missing = new Context(ctx.outbound(), ctx.inbound(), true, ctx.coverage(), 1L, T);
    assertThat(engine.evaluate(current(95), missing).result().isFraud()).isNull();
  }

  @Test
  void newAccountsAcceptCompleteCoverageSinceOpeningButStillNeedTwentySamples() {
    Coverage coverage =
        new Coverage(
            "A",
            T.minusSeconds(172800),
            T.minusSeconds(172800),
            T,
            T.minusSeconds(172800),
            T,
            true);
    Context ctx = new Context(baseline(T), List.of(), false, coverage, 20L, T);
    assertThat(engine.evaluate(current(100), ctx).result().evaluationStatus())
        .isEqualTo("COMPLETE");
    Context fewer = new Context(baseline(T).subList(0, 19), List.of(), false, coverage, 19L, T);
    assertThat(engine.evaluate(current(100), fewer).result().evaluationStatus())
        .isEqualTo("PARTIAL");
  }

  @Test
  void multiUsesAllTransfersAfterLatestPostingAndSuppressesRapid() {
    List<Transfer> inbound = multiInbound();
    List<Transfer> outbound = new ArrayList<>(baseline(T));
    outbound.add(tx("first-out", "A", "D", 800, T.minusSeconds(30), null));
    // Last inbound is 1000, all inbound total 2000, outgoing total 1800: below 95%.
    assertNoMulti(engine.evaluate(current(1000), complete(outbound, inbound)));
    outbound.removeLast();
    outbound.add(tx("first-out", "A", "D", 900, T.minusSeconds(30), null));
    Evaluation evaluated = engine.evaluate(current(1000), complete(outbound, inbound));
    assertThat(evaluated.result().riskScore()).isEqualTo(100);
    assertThat(evaluated.result().triggeredRules())
        .anySatisfy(
            r -> {
              assertThat(r.ruleCode()).isEqualTo("RAPID_PASS_THROUGH");
              assertThat(r.scoreContribution()).isZero();
              assertThat(r.suppressedBy()).isEqualTo("MULTI_ACCOUNT_FLOW");
            });
    outbound.add(tx("at-posting", "A", "D", 99999, T.minusSeconds(60), null));
    assertThat(engine.evaluate(current(1000), complete(outbound, inbound)).result().riskScore())
        .isEqualTo(100);
    outbound.add(tx("extra", "A", "D", 101, T.minusSeconds(10), null));
    assertThat(
            engine.evaluate(current(1000), complete(outbound, inbound)).result().triggeredRules())
        .noneMatch(r -> r.ruleCode().equals("MULTI_ACCOUNT_FLOW"));
  }

  @Test
  void multiRejectsFourSourcesOneTargetAndIntersectingAccounts() {
    List<Transfer> outbound = new ArrayList<>(baseline(T));
    outbound.add(tx("other-out", "A", "D", 900, T.minusSeconds(30), null));
    List<Transfer> incoming = multiInbound();
    assertNoMulti(engine.evaluate(current(1000), complete(outbound, incoming.subList(1, 5))));
    outbound.set(outbound.size() - 1, tx("other-out", "A", "B", 900, T.minusSeconds(30), null));
    assertNoMulti(engine.evaluate(current(1000), complete(outbound, incoming)));
    outbound.set(
        outbound.size() - 1, tx("other-out", "A", "source0", 900, T.minusSeconds(30), null));
    assertNoMulti(engine.evaluate(current(1000), complete(outbound, incoming)));
  }

  @Test
  void combinesGroupsCapsScoreAndGeneratesStableEvidenceIds() {
    List<Transfer> history = new ArrayList<>(baseline(T));
    for (int i = 0; i < 11; i++) {
      history.add(tx("burst" + i, "A", "other", 100, T.minusSeconds(i), null));
    }
    Evaluation evaluated = engine.evaluate(current(1000), complete(history, List.of()));
    assertThat(evaluated.result().riskScore()).isEqualTo(95);
    Instant night = Instant.parse("2026-10-01T19:00:00Z");
    Transfer nightTx = tx("night", "A", "B", 1000, night, null);
    List<Transfer> shifted =
        history.stream()
            .map(
                h ->
                    tx(
                        h.transactionRef(),
                        h.senderAccountId(),
                        h.receiverAccountId(),
                        h.amountVnd().longValueExact(),
                        h.transactionTime().plusSeconds(14 * 3600),
                        null))
            .toList();
    assertThat(engine.evaluate(nightTx, complete(shifted, List.of())).result().riskScore())
        .isEqualTo(100);
    assertThat(engine.evaluate(current(1000), complete(history, List.of())).rules())
        .isEqualTo(evaluated.rules());
    assertThat(evaluated.rules().stream().filter(r -> r.status().equals("MATCHED")))
        .allSatisfy(r -> assertThat(r.riskEventId()).matches("[0-9a-f]{64}"));
  }

  @ParameterizedTest
  @CsvSource({
    "0,LOW",
    "29,LOW",
    "30,MEDIUM",
    "59,MEDIUM",
    "60,HIGH",
    "79,HIGH",
    "80,CRITICAL",
    "100,CRITICAL"
  })
  void levelBoundaries(int score, RiskLevel level) {
    assertThat(RiskScoreEngine.level(score)).isEqualTo(level);
  }

  private void assertNoMulti(Evaluation evaluation) {
    assertThat(evaluation.result().triggeredRules())
        .noneMatch(r -> r.ruleCode().equals("MULTI_ACCOUNT_FLOW"));
  }

  private List<Transfer> multiInbound() {
    List<Transfer> inbound = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      inbound.add(
          tx(
              "in" + i,
              "source" + i,
              "A",
              i == 4 ? 1000 : 250,
              T.minusSeconds(300),
              T.minusSeconds(100 - 10L * i)));
    }
    return inbound;
  }

  private Context complete(List<Transfer> outbound, List<Transfer> inbound) {
    return new Context(
        outbound,
        inbound,
        false,
        new Coverage(
            "A",
            null,
            T.minusSeconds(40L * 86400),
            T.plusSeconds(86400),
            T.minusSeconds(40L * 86400),
            T.plusSeconds(86400),
            true),
        100L,
        T);
  }

  private List<Transfer> baseline(Instant t) {
    return IntStream.range(0, 20)
        .mapToObj(i -> tx("base" + i, "A", "old", 100, t.minusSeconds(86400 + i), null))
        .toList();
  }

  private Transfer current(long amount) {
    return tx("current", "A", "B", amount, T, null);
  }

  private Transfer tx(
      String ref, String from, String to, long amount, Instant time, Instant posting) {
    return new Transfer(ref, from, to, BigDecimal.valueOf(amount), "VND", time, posting);
  }
}
