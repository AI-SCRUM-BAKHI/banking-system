package com.scrumai.fraud.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Immutable startup configuration shared by the engine and both context repositories. */
@Component
public class RiskRuleConfiguration {
  private static final String PREFIX = "APP_FRAUD_";
  private final String version;
  private final Map<String, Object> parameters;

  @Autowired
  public RiskRuleConfiguration(Environment environment) {
    this(
        environment.getProperty(
            "app.fraud.rules.version",
            environment.getProperty("APP_FRAUD_RULE_VERSION", RiskScoreEngine.VERSION)),
        environment);
  }

  public RiskRuleConfiguration(String version) {
    this(version, new AbstractEnvironment() {});
  }

  public RiskRuleConfiguration(String version, Environment environment) {
    if (!RiskScoreEngine.VERSION.equals(version)
        && !RiskScoreEngine.V2.equals(version)
        && !RiskScoreEngine.V3_RECALL.equals(version)) {
      throw new IllegalArgumentException("Unsupported fraud rule version");
    }
    this.version = version;
    boolean enhanced = !RiskScoreEngine.VERSION.equals(version);
    boolean recall = RiskScoreEngine.V3_RECALL.equals(version);
    Map<String, Object> values = new LinkedHashMap<>();
    integer(values, environment, "THRESHOLD", 60, 1, 100);
    integer(values, environment, "BASELINE_DAYS", 30, 1, 365);
    integer(values, environment, "BASELINE_MIN_COUNT", 20, 1, 100000);
    decimal(values, environment, "AMOUNT_SPIKE_MULTIPLIER", recall ? "4" : "8");
    decimal(values, environment, "NEW_BENEFICIARY_MULTIPLIER", recall ? "2.5" : "8");
    integer(values, environment, "AMOUNT_SPIKE_SCORE", enhanced ? 60 : 35, 0, 100);
    integer(values, environment, "NEW_BENEFICIARY_SCORE", enhanced ? 65 : 50, 0, 100);
    integer(values, environment, "UNUSUAL_HOUR_SCORE", recall ? 60 : 25, 0, 100);
    integer(values, environment, "UNUSUAL_HOUR_START", 2, 0, 23);
    integer(values, environment, "UNUSUAL_HOUR_END", 4, 1, 24);
    integer(values, environment, "FREQUENCY_SCORE", enhanced ? 65 : 45, 0, 100);
    integer(values, environment, "FREQUENCY_WINDOW_SECONDS", enhanced ? 120 : 600, 1, 86400);
    integer(values, environment, "FREQUENCY_MIN_COUNT", recall ? 2 : enhanced ? 3 : 12, 2, 100000);
    integer(values, environment, "SPLIT_SCORE", enhanced ? 70 : 55, 0, 100);
    integer(values, environment, "SPLIT_WINDOW_SECONDS", 3600, 1, 86400);
    integer(values, environment, "SPLIT_MIN_COUNT", recall ? 2 : enhanced ? 3 : 9, 2, 100000);
    integer(
        values,
        environment,
        "SPLIT_MIN_GAP_SECONDS",
        recall ? 120 : enhanced ? 240 : 300,
        1,
        86400);
    integer(
        values,
        environment,
        "SPLIT_MAX_GAP_SECONDS",
        recall ? 900 : enhanced ? 480 : 420,
        1,
        86400);
    integer(values, environment, "RAPID_SCORE", enhanced && !recall ? 75 : 65, 0, 100);
    integer(values, environment, "RAPID_MIN_DELAY_SECONDS", recall ? 30 : 60, 1, 86400);
    integer(values, environment, "RAPID_MAX_DELAY_SECONDS", recall ? 600 : 120, 1, 86400);
    decimal(values, environment, "RAPID_MIN_RATIO", recall ? "0.8" : "0.95");
    decimal(values, environment, "RAPID_MAX_RATIO", recall ? "1.2" : "1");
    integer(values, environment, "MULTI_SCORE", 80, 0, 100);
    integer(values, environment, "MULTI_WINDOW_SECONDS", enhanced ? 1800 : 600, 1, 86400);
    integer(values, environment, "MULTI_MIN_SOURCES", recall ? 3 : 5, 2, 100000);
    integer(values, environment, "MULTI_MIN_TARGETS", recall ? 1 : 2, 1, 100000);
    decimal(values, environment, "MULTI_MIN_RATIO", recall ? "0.4" : "0.95");
    decimal(values, environment, "MULTI_MAX_RATIO", recall ? "1.2" : "1");
    parameters = Map.copyOf(values);
    ordered("UNUSUAL_HOUR_START", "UNUSUAL_HOUR_END", true);
    ordered("SPLIT_MIN_GAP_SECONDS", "SPLIT_MAX_GAP_SECONDS", false);
    ordered("RAPID_MIN_DELAY_SECONDS", "RAPID_MAX_DELAY_SECONDS", false);
    ordered("RAPID_MIN_RATIO", "RAPID_MAX_RATIO", false);
    ordered("MULTI_MIN_RATIO", "MULTI_MAX_RATIO", false);
    if ((long) (integer("SPLIT_MIN_COUNT") - 1) * integer("SPLIT_MIN_GAP_SECONDS")
        >= integer("SPLIT_WINDOW_SECONDS")) {
      throw new IllegalArgumentException(
          "APP_FRAUD_SPLIT_WINDOW_SECONDS cannot contain the configured split");
    }
  }

  public String version() {
    return version;
  }

  public int integer(String key) {
    return ((Number) parameters.get(key)).intValue();
  }

  public BigDecimal decimal(String key) {
    return new BigDecimal(parameters.get(key).toString());
  }

  public Map<String, Object> snapshot() {
    return parameters;
  }

  public long outboundContextSeconds() {
    return Math.max(
        (long) integer("BASELINE_DAYS") * 86400 + integer("SPLIT_WINDOW_SECONDS"),
        Math.max(integer("FREQUENCY_WINDOW_SECONDS"), integer("MULTI_WINDOW_SECONDS")));
  }

  public int inboundContextSeconds() {
    // Retain the legacy context superset; larger configured windows expand it.
    return Math.max(
        1800, Math.max(integer("RAPID_MAX_DELAY_SECONDS"), integer("MULTI_WINDOW_SECONDS")));
  }

  private void ordered(String lower, String upper, boolean strict) {
    int comparison = decimal(lower).compareTo(decimal(upper));
    if (comparison > 0 || (strict && comparison == 0)) {
      throw new IllegalArgumentException(
          PREFIX + lower + " must be " + (strict ? "< " : "<= ") + PREFIX + upper);
    }
  }

  private static void integer(
      Map<String, Object> values, Environment env, String key, int fallback, int min, int max) {
    String raw = env.getProperty(PREFIX + key);
    int value;
    try {
      value = raw == null ? fallback : Integer.parseInt(raw.trim());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(PREFIX + key + " must be an integer");
    }
    if (value < min || value > max) {
      throw new IllegalArgumentException(PREFIX + key + " must be between " + min + " and " + max);
    }
    values.put(key, value);
  }

  private static void decimal(
      Map<String, Object> values, Environment env, String key, String fallback) {
    BigDecimal value;
    try {
      value = new BigDecimal(env.getProperty(PREFIX + key, fallback).trim());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(PREFIX + key + " must be a decimal");
    }
    if (value.signum() <= 0
        || value.compareTo(new BigDecimal("1000")) > 0
        || value.scale() > 6
        || value.precision() > 10) {
      throw new IllegalArgumentException(
          PREFIX
              + key
              + " must be greater than 0 and at most 1000, with at most 6 decimal places and 10 significant digits");
    }
    values.put(key, value);
  }
}
