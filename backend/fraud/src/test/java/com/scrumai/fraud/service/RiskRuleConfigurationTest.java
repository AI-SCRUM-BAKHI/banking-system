package com.scrumai.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scrumai.fraud.service.RiskModels.Context;
import com.scrumai.fraud.service.RiskModels.Coverage;
import com.scrumai.fraud.service.RiskModels.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class RiskRuleConfigurationTest {
  private static final Instant T = Instant.parse("2026-10-01T05:00:00Z");

  @Test
  void springResolvesEnvironmentOverridesAndWiresTheEngine() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.setEnvironment(
          new MockEnvironment()
              .withProperty("APP_FRAUD_RULE_VERSION", RiskScoreEngine.V3_RECALL)
              .withProperty("APP_FRAUD_THRESHOLD", "75")
              .withProperty("APP_FRAUD_UNUSUAL_HOUR_SCORE", "77"));
      context.register(RiskRuleConfiguration.class, RiskScoreEngine.class);
      context.refresh();
      var engine = context.getBean(RiskScoreEngine.class);
      var result =
          engine
              .evaluate(tx("night", "B", 100, Instant.parse("2026-10-01T19:00:00Z")), empty())
              .result();
      assertThat(result.riskScore()).isEqualTo(77);
      assertThat(result.fraudThreshold()).isEqualTo(75);
      assertThat(result.isFraud()).isTrue();
      assertThat(result.ruleVersion()).isEqualTo(RiskScoreEngine.V3_RECALL);
      assertThat(
              ((Map<?, ?>) engine.configuration().get("parameters"))
                  .containsKey("UNUSUAL_HOUR_SCORE"))
          .isTrue();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "APP_FRAUD_THRESHOLD,0",
    "APP_FRAUD_THRESHOLD,101",
    "APP_FRAUD_THRESHOLD,abc",
    "APP_FRAUD_FREQUENCY_WINDOW_SECONDS,0",
    "APP_FRAUD_FREQUENCY_WINDOW_SECONDS,86401",
    "APP_FRAUD_AMOUNT_SPIKE_SCORE,-1",
    "APP_FRAUD_RAPID_MIN_RATIO,NaN",
    "APP_FRAUD_RAPID_MIN_RATIO,0",
    "APP_FRAUD_RAPID_MIN_RATIO,0.0000001",
    "APP_FRAUD_RAPID_MIN_RATIO,2",
    "APP_FRAUD_MULTI_MAX_RATIO,0.1",
    "APP_FRAUD_UNUSUAL_HOUR_START,4",
    "APP_FRAUD_SPLIT_MIN_GAP_SECONDS,1000",
    "APP_FRAUD_SPLIT_WINDOW_SECONDS,120",
    "APP_FRAUD_RAPID_MIN_DELAY_SECONDS,601",
    "APP_FRAUD_BASELINE_MIN_COUNT,0",
    "APP_FRAUD_MULTI_MIN_SOURCES,1"
  })
  void rejectsInvalidConfigurationAtStartup(String key, String value) {
    assertThatThrownBy(() -> configured(key, value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("APP_FRAUD_");
  }

  @Test
  void thresholdScoreAndHourOverridesAffectDecisions() {
    var night = tx("night", "B", 100, Instant.parse("2026-10-01T19:00:00Z"));
    assertThat(
            new RiskScoreEngine(configured("APP_FRAUD_THRESHOLD", "61"))
                .evaluate(night, empty())
                .result()
                .isFraud())
        .isNull();
    assertThat(
            new RiskScoreEngine(
                    configured(
                        "APP_FRAUD_UNUSUAL_HOUR_END", "2", "APP_FRAUD_UNUSUAL_HOUR_START", "1"))
                .evaluate(night, empty())
                .result()
                .riskScore())
        .isZero();
    assertThat(
            new RiskScoreEngine(configured("APP_FRAUD_UNUSUAL_HOUR_SCORE", "0"))
                .evaluate(night, empty())
                .result()
                .riskScore())
        .isZero();
  }

  @Test
  void frequencyWindowAndMinimumCountAffectTheActualRule() {
    var history = List.of(tx("previous", "B", 100, T.minusSeconds(300)));
    var current = tx("current", "B", 100, T);
    assertThat(
            new RiskScoreEngine(RiskScoreEngine.V3_RECALL)
                .evaluate(current, context(history, List.of()))
                .result()
                .isFraud())
        .isNull();
    assertThat(
            new RiskScoreEngine(configured("APP_FRAUD_FREQUENCY_WINDOW_SECONDS", "600"))
                .evaluate(current, context(history, List.of()))
                .result()
                .isFraud())
        .isTrue();
    assertThat(
            new RiskScoreEngine(
                    configured(
                        "APP_FRAUD_FREQUENCY_WINDOW_SECONDS",
                        "600",
                        "APP_FRAUD_FREQUENCY_MIN_COUNT",
                        "3"))
                .evaluate(current, context(history, List.of()))
                .result()
                .isFraud())
        .isNull();
  }

  @Test
  void amountMultipliersBaselineCountAndDurationAreConfigurable() {
    var history =
        IntStream.range(0, 3)
            .mapToObj(i -> tx("old" + i, "known", 100, T.minusSeconds(3 * 86400L + i)))
            .toList();
    var cfg =
        configured(
            "APP_FRAUD_BASELINE_MIN_COUNT",
            "3",
            "APP_FRAUD_AMOUNT_SPIKE_MULTIPLIER",
            "3.5",
            "APP_FRAUD_NEW_BENEFICIARY_MULTIPLIER",
            "2");
    var engine = new RiskScoreEngine(cfg);
    assertThat(
            engine
                .evaluate(tx("current", "known", 351, T), context(history, List.of()))
                .result()
                .riskScore())
        .isEqualTo(60);
    assertThat(
            engine
                .evaluate(tx("current", "new", 201, T), context(history, List.of()))
                .result()
                .riskScore())
        .isEqualTo(65);
    var shortBaseline =
        configured("APP_FRAUD_BASELINE_MIN_COUNT", "3", "APP_FRAUD_BASELINE_DAYS", "1");
    assertThat(
            new RiskScoreEngine(shortBaseline)
                .evaluate(tx("current", "known", 1000, T), context(history, List.of()))
                .rules())
        .anyMatch(r -> r.ruleCode().equals("AMOUNT_SPIKE") && r.status().equals("NOT_EVALUATED"));
  }

  @Test
  void flowWindowsAndRatiosAffectMatchingAndContextLookbacks() {
    var incoming =
        new Transfer(
            "credit",
            "C",
            "A",
            BigDecimal.valueOf(100),
            "VND",
            T.minusSeconds(4000),
            T.minusSeconds(3600));
    var current = tx("current", "B", 150, T);
    var cfg =
        configured(
            "APP_FRAUD_RAPID_MAX_DELAY_SECONDS",
            "7200",
            "APP_FRAUD_RAPID_MAX_RATIO",
            "1.5",
            "APP_FRAUD_MULTI_WINDOW_SECONDS",
            "10800",
            "APP_FRAUD_BASELINE_DAYS",
            "60",
            "APP_FRAUD_SPLIT_WINDOW_SECONDS",
            "7200");
    assertThat(cfg.inboundContextSeconds()).isEqualTo(10800);
    assertThat(cfg.outboundContextSeconds()).isEqualTo(60L * 86400 + 7200);
    assertThat(
            new RiskScoreEngine(cfg)
                .evaluate(current, context(List.of(), List.of(incoming)))
                .result()
                .isFraud())
        .isTrue();
    assertThat(
            new RiskScoreEngine(RiskScoreEngine.V3_RECALL)
                .evaluate(current, context(List.of(), List.of(incoming)))
                .result()
                .isFraud())
        .isNull();
  }

  @Test
  void configurationSnapshotIsImmutableAndIncludesAllOverrides() {
    var cfg = configured("APP_FRAUD_MULTI_SCORE", "99", "APP_FRAUD_SPLIT_MAX_GAP_SECONDS", "1200");
    assertThat(cfg.snapshot())
        .containsEntry("MULTI_SCORE", 99)
        .containsEntry("SPLIT_MAX_GAP_SECONDS", 1200);
    assertThatThrownBy(() -> cfg.snapshot().put("THRESHOLD", 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private RiskRuleConfiguration configured(String... pairs) {
    var env = new MockEnvironment();
    for (int i = 0; i < pairs.length; i += 2) {
      env.withProperty(pairs[i], pairs[i + 1]);
    }
    return new RiskRuleConfiguration(RiskScoreEngine.V3_RECALL, env);
  }

  private Context empty() {
    return new Context(List.of(), List.of(), false, null, null, null);
  }

  private Context context(List<Transfer> outbound, List<Transfer> inbound) {
    return new Context(
        outbound,
        inbound,
        false,
        new Coverage(
            "A", null, T.minusSeconds(90L * 86400), T, T.minusSeconds(90L * 86400), T, true),
        null,
        null);
  }

  private Transfer tx(String ref, String receiver, long amount, Instant time) {
    return new Transfer(ref, "A", receiver, BigDecimal.valueOf(amount), "VND", time, null);
  }
}
