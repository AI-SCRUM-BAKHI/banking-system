package com.scrumai.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.scrumai.fraud.service.RiskModels.Context;
import com.scrumai.fraud.service.RiskModels.Coverage;
import com.scrumai.fraud.service.RiskModels.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RiskScoreEngineV3RecallTest {
  private static final Instant T = Instant.parse("2026-10-01T05:00:00Z");
  private final RiskScoreEngine engine = new RiskScoreEngine(RiskScoreEngine.V3_RECALL);

  @ParameterizedTest
  @CsvSource({"known,400,false", "known,401,true", "new,250,false", "new,251,true"})
  void exactRecallAmountAndNewBeneficiaryBoundaries(
      String receiver, long amount, boolean expected) {
    var result =
        engine
            .evaluate(tx("now", "A", receiver, amount, T, null), context(baseline(), List.of()))
            .result();
    assertThat(result.isFraud()).isEqualTo(expected);
    assertThat(result.ruleVersion()).isEqualTo(RiskScoreEngine.V3_RECALL);
  }

  @Test
  void unusualHourCanAlertEvenWithMissingBaselineButStillReportsPartial() {
    var night = Instant.parse("2026-10-01T19:00:00Z");
    var result =
        engine
            .evaluate(
                tx("night", "A", "B", 100, night, null),
                new Context(List.of(), List.of(), false, null, null, null))
            .result();
    assertThat(result.riskScore()).isEqualTo(60);
    assertThat(result.isFraud()).isTrue();
    assertThat(result.evaluationStatus()).isEqualTo("PARTIAL");
  }

  @Test
  void secondTransferAlertsWithoutNeedingAFutureThirdTransfer() {
    var history = new ArrayList<>(baseline());
    history.add(tx("first", "A", "B", 100, T.minusSeconds(39), null));
    assertThat(
            engine
                .evaluate(tx("now", "A", "B", 100, T, null), context(history, List.of()))
                .result()
                .isFraud())
        .isTrue();
    history.removeLast();
    history.add(tx("future", "A", "B", 100, T.plusSeconds(1), null));
    history.add(tx("boundary", "A", "B", 100, T.minusSeconds(120), null));
    assertThat(
            engine
                .evaluate(tx("now", "A", "B", 100, T, null), context(history, List.of()))
                .result()
                .isFraud())
        .isFalse();
  }

  @Test
  void splitLinkEvidenceContainsOnlyPatternMembersNotOldBaseline() {
    var history = new ArrayList<>(baseline());
    history.add(tx("first-split", "A", "known", 100, T.minusSeconds(360), null));
    history.add(tx("unrelated-payment", "A", "known", 101, T.minusSeconds(180), null));
    var evaluated =
        engine.evaluate(tx("now", "A", "known", 100, T, null), context(history, List.of()));
    assertThat(evaluated.result().isFraud()).isTrue();
    var audit =
        evaluated.rules().stream()
            .filter(rule -> rule.ruleCode().equals("STRUCTURED_SPLIT"))
            .findFirst()
            .orElseThrow();
    assertThat(audit.evidence()).contains("baseline0");
    assertThat(audit.features().get("patternTransactionRefs"))
        .isEqualTo(List.of("first-split", "now"));
  }

  @ParameterizedTest
  @CsvSource({
    "30,80,true",
    "600,120,true",
    "29,100,false",
    "601,100,false",
    "60,79,false",
    "60,121,false"
  })
  void rapidRecallKeepsExactTimeAndRatioBounds(int seconds, long amount, boolean expected) {
    var incoming = tx("credit", "C", "A", 100, T.minusSeconds(1000), T.minusSeconds(seconds));
    var evaluated =
        engine.evaluate(
            tx("now", "A", "B", amount, T, null), context(baseline(), List.of(incoming)));
    assertThat(
            evaluated.result().triggeredRules().stream()
                .anyMatch(rule -> rule.ruleCode().equals("RAPID_PASS_THROUGH")))
        .isEqualTo(expected);
  }

  @Test
  void rapidRecallUsesLatestValidCreditWithoutAssumingUnpostedCredits() {
    var good = tx("good", "C", "A", 100, T.minusSeconds(100), T.minusSeconds(90));
    var incompatible = tx("recent", "D", "A", 10000, T.minusSeconds(60), T.minusSeconds(30));
    var evaluated =
        engine.evaluate(
            tx("now", "A", "B", 100, T, null), context(List.of(), List.of(good, incompatible)));
    assertThat(evaluated.result().isFraud()).isTrue();
    assertThat(evaluated.result().evaluationStatus()).isEqualTo("PARTIAL");
    var base = context(List.of(), List.of(good));
    var unknown = new Context(base.outbound(), base.inbound(), true, base.coverage(), null, null);
    assertThat(engine.evaluate(tx("now", "A", "B", 100, T, null), unknown).result().isFraud())
        .isNull();
  }

  @Test
  void partialFanInForwardingCanAlertButNotAFlowReturningToASource() {
    var incoming =
        IntStream.range(0, 3)
            .mapToObj(
                i ->
                    tx(
                        "credit" + i,
                        "source" + i,
                        "A",
                        600,
                        T.minusSeconds(1000),
                        T.minusSeconds(900 - 60L * i)))
            .toList();
    var evaluated =
        engine.evaluate(tx("now", "A", "B", 900, T, null), context(List.of(), incoming));
    assertThat(evaluated.result().isFraud()).isTrue();
    assertThat(evaluated.result().triggeredRules())
        .anyMatch(rule -> rule.ruleCode().equals("MULTI_ACCOUNT_FLOW"));
    var circular =
        engine.evaluate(tx("now", "A", "source0", 900, T, null), context(List.of(), incoming));
    assertThat(circular.result().triggeredRules())
        .noneMatch(rule -> rule.ruleCode().equals("MULTI_ACCOUNT_FLOW"));
  }

  private Context context(List<Transfer> outbound, List<Transfer> inbound) {
    return new Context(
        outbound,
        inbound,
        false,
        new Coverage(
            "A", null, T.minusSeconds(40L * 86400), T, T.minusSeconds(40L * 86400), T, true),
        null,
        null);
  }

  private List<Transfer> baseline() {
    return IntStream.range(0, 20)
        .mapToObj(i -> tx("baseline" + i, "A", "known", 100, T.minusSeconds(86400 + i), null))
        .toList();
  }

  private Transfer tx(
      String ref, String from, String to, long amount, Instant time, Instant posted) {
    return new Transfer(ref, from, to, BigDecimal.valueOf(amount), "VND", time, posted);
  }
}
