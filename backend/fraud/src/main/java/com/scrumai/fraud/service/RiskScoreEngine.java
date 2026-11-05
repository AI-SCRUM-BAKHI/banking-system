package com.scrumai.fraud.service;

import com.scrumai.fraud.enums.RiskLevel;
import com.scrumai.fraud.service.RiskModels.Context;
import com.scrumai.fraud.service.RiskModels.Coverage;
import com.scrumai.fraud.service.RiskModels.Evaluation;
import com.scrumai.fraud.service.RiskModels.Result;
import com.scrumai.fraud.service.RiskModels.RuleAudit;
import com.scrumai.fraud.service.RiskModels.Transfer;
import com.scrumai.fraud.service.RiskModels.TriggeredRule;
import com.scrumai.fraud.service.RiskModels.UnevaluatedRule;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RiskScoreEngine {
  public static final String VERSION = "rule-score-v1";
  public static final String V2 = "rule-score-v2";
  public static final String V3_RECALL = "rule-score-v3-recall";
  private static final List<List<String>> GROUPS =
      List.of(
          List.of("AMOUNT_SPIKE", "NEW_BENEFICIARY_LARGE"),
          List.of("FREQUENCY_SPIKE", "STRUCTURED_SPLIT"),
          List.of("RAPID_PASS_THROUGH", "MULTI_ACCOUNT_FLOW"),
          List.of("UNUSUAL_HOUR"));
  private final String version;
  private final boolean enhanced;
  private final boolean highRecall;
  private final Map<String, Integer> weights;
  private final RiskRuleConfiguration config;
  private final Duration baselineDuration;

  public RiskScoreEngine() {
    this(VERSION);
  }

  public RiskScoreEngine(String version) {
    this(new RiskRuleConfiguration(version));
  }

  @Autowired
  public RiskScoreEngine(RiskRuleConfiguration config) {
    this.config = config;
    version = config.version();
    enhanced = !VERSION.equals(version);
    highRecall = V3_RECALL.equals(version);
    baselineDuration = Duration.ofDays(config.integer("BASELINE_DAYS"));
    weights =
        Map.of(
            "AMOUNT_SPIKE", config.integer("AMOUNT_SPIKE_SCORE"),
            "NEW_BENEFICIARY_LARGE", config.integer("NEW_BENEFICIARY_SCORE"),
            "UNUSUAL_HOUR", config.integer("UNUSUAL_HOUR_SCORE"),
            "FREQUENCY_SPIKE", config.integer("FREQUENCY_SCORE"),
            "STRUCTURED_SPLIT", config.integer("SPLIT_SCORE"),
            "RAPID_PASS_THROUGH", config.integer("RAPID_SCORE"),
            "MULTI_ACCOUNT_FLOW", config.integer("MULTI_SCORE"));
  }

  public Map<String, Object> configuration() {
    return Map.of(
        "ruleVersion",
        version,
        "fraudThreshold",
        config.integer("THRESHOLD"),
        "weights",
        weights,
        "groups",
        GROUPS,
        "parameters",
        config.snapshot(),
        "hourTimezone",
        "Asia/Ho_Chi_Minh",
        "splitEqualAmountFiltering",
        highRecall,
        "rapidRequiresAmountSpike",
        enhanced && !highRecall,
        "rapidLatestValidCredit",
        highRecall,
        "score",
        "sum group maxima; cap 100; levels 30/60/80");
  }

  public Evaluation evaluate(Transfer current, Context context) {
    List<RuleAudit> rules = new ArrayList<>();
    amount(current, context, rules);
    int hour = current.transactionTime().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).getHour();
    rules.add(
        rule(
            "UNUSUAL_HOUR",
            hour >= config.integer("UNUSUAL_HOUR_START")
                && hour < config.integer("UNUSUAL_HOUR_END"),
            current,
            List.of(current),
            Map.of("localHour", hour)));
    frequency(current, context, rules);
    split(current, context, rules);
    rapid(current, context, rules);
    multi(current, context, rules);

    List<TriggeredRule> triggered = new ArrayList<>();
    for (List<String> group : GROUPS) {
      List<String> matched =
          rules.stream()
              .filter(r -> r.status().equals("MATCHED") && group.contains(r.ruleCode()))
              .map(RuleAudit::ruleCode)
              .toList();
      String winner = matched.stream().max(Comparator.comparingInt(weights::get)).orElse(null);
      for (String code : matched) {
        triggered.add(
            new TriggeredRule(
                code,
                code.equals(winner) ? weights.get(code) : 0,
                code.equals(winner) ? null : winner));
      }
    }
    List<UnevaluatedRule> missing =
        rules.stream()
            .filter(r -> r.status().equals("NOT_EVALUATED"))
            .map(r -> new UnevaluatedRule(r.ruleCode(), r.reason()))
            .toList();
    int score = Math.min(100, triggered.stream().mapToInt(TriggeredRule::scoreContribution).sum());
    Boolean fraud =
        score >= config.integer("THRESHOLD")
            ? Boolean.TRUE
            : missing.isEmpty() ? Boolean.FALSE : null;
    return new Evaluation(
        new Result(
            current.transactionRef(),
            fraud,
            Boolean.TRUE.equals(fraud)
                ? "FRAUD"
                : fraud == null ? "INSUFFICIENT_DATA" : "NOT_FRAUD",
            score,
            level(score),
            missing.isEmpty() ? "COMPLETE" : "PARTIAL",
            config.integer("THRESHOLD"),
            version,
            List.copyOf(triggered),
            missing),
        List.copyOf(rules));
  }

  public static RiskLevel level(int score) {
    return score >= 80
        ? RiskLevel.CRITICAL
        : score >= 60 ? RiskLevel.HIGH : score >= 30 ? RiskLevel.MEDIUM : RiskLevel.LOW;
  }

  private void amount(Transfer tx, Context ctx, List<RuleAudit> rules) {
    Instant t = tx.transactionTime();
    List<Transfer> baseline =
        ctx.outbound().stream()
            .filter(
                h ->
                    !h.transactionTime().isBefore(t.minus(baselineDuration))
                        && h.transactionTime().isBefore(t))
            .toList();
    if (!outbound(ctx, t.minus(baselineDuration), t)
        || baseline.size() < config.integer("BASELINE_MIN_COUNT")) {
      String reason =
          "Requires complete outbound coverage and at least "
              + config.integer("BASELINE_MIN_COUNT")
              + " baseline samples";
      rules.add(missing("AMOUNT_SPIKE", reason));
      rules.add(missing("NEW_BENEFICIARY_LARGE", reason));
      return;
    }
    List<BigDecimal> amounts = baseline.stream().map(Transfer::amountVnd).sorted().toList();
    int n = amounts.size();
    BigDecimal median =
        n % 2 == 1
            ? amounts.get(n / 2)
            : amounts.get(n / 2 - 1).add(amounts.get(n / 2)).divide(BigDecimal.valueOf(2));
    boolean spike =
        tx.amountVnd().compareTo(median.multiply(config.decimal("AMOUNT_SPIKE_MULTIPLIER"))) > 0;
    boolean known =
        baseline.stream().anyMatch(h -> h.receiverAccountId().equals(tx.receiverAccountId()));
    Map<String, Object> features =
        Map.of(
            "baselineMedianVnd",
            median,
            "baselineCount",
            n,
            "amountVnd",
            tx.amountVnd(),
            "knownReceiver",
            known);
    List<Transfer> evidence = new ArrayList<>(baseline);
    evidence.add(tx);
    rules.add(rule("AMOUNT_SPIKE", spike, tx, evidence, features));
    boolean newLarge =
        tx.amountVnd().compareTo(median.multiply(config.decimal("NEW_BENEFICIARY_MULTIPLIER"))) > 0;
    rules.add(rule("NEW_BENEFICIARY_LARGE", newLarge && !known, tx, evidence, features));
  }

  private void frequency(Transfer tx, Context ctx, List<RuleAudit> rules) {
    int window = config.integer("FREQUENCY_WINDOW_SECONDS");
    Instant start = tx.transactionTime().minusSeconds(window);
    if (!outbound(ctx, start, tx.transactionTime())) {
      rules.add(
          missing("FREQUENCY_SPIKE", "Requires complete " + window + "-second outbound coverage"));
      return;
    }
    List<Transfer> evidence = rolling(tx, ctx, start);
    rules.add(
        rule(
            "FREQUENCY_SPIKE",
            evidence.size() >= config.integer("FREQUENCY_MIN_COUNT"),
            tx,
            evidence,
            Map.of("outboundCount", evidence.size())));
  }

  private void split(Transfer tx, Context ctx, List<RuleAudit> rules) {
    Instant t = tx.transactionTime();
    if (!outbound(ctx, t.minusSeconds(config.integer("SPLIT_WINDOW_SECONDS")), t)) {
      rules.add(
          missing(
              "STRUCTURED_SPLIT",
              "Requires complete "
                  + config.integer("SPLIT_WINDOW_SECONDS")
                  + "-second outbound coverage"));
      return;
    }
    // List order is ingest order; stable sorting preserves it for equal timestamps.
    List<Transfer> pair =
        rolling(tx, ctx, t.minusSeconds(config.integer("SPLIT_WINDOW_SECONDS"))).stream()
            .filter(h -> h.receiverAccountId().equals(tx.receiverAccountId()))
            .filter(h -> !highRecall || h.amountVnd().compareTo(tx.amountVnd()) == 0)
            .sorted(Comparator.comparing(Transfer::transactionTime))
            .toList();
    int required = config.integer("SPLIT_MIN_COUNT");
    List<Transfer> group = pair.subList(Math.max(0, pair.size() - required), pair.size());
    boolean matches = group.size() == required;
    for (int i = 0; matches && i < group.size(); i++) {
      matches = group.get(i).amountVnd().compareTo(tx.amountVnd()) == 0;
      if (i > 0) {
        Duration gap =
            Duration.between(group.get(i - 1).transactionTime(), group.get(i).transactionTime());
        matches &=
            gap.compareTo(Duration.ofSeconds(config.integer("SPLIT_MIN_GAP_SECONDS"))) >= 0
                && gap.compareTo(Duration.ofSeconds(config.integer("SPLIT_MAX_GAP_SECONDS"))) <= 0;
      }
    }
    List<Transfer> evidence = new ArrayList<>(group);
    if (matches) {
      Instant first = group.getFirst().transactionTime();
      if (!outbound(ctx, first.minus(baselineDuration), first)) {
        rules.add(
            missing("STRUCTURED_SPLIT", "Requires receiver history coverage before first split"));
        return;
      }
      List<Transfer> prior =
          ctx.outbound().stream()
              .filter(
                  h ->
                      h.receiverAccountId().equals(tx.receiverAccountId())
                          && !h.transactionTime().isBefore(first.minus(baselineDuration))
                          && h.transactionTime().isBefore(first))
              .toList();
      matches = !prior.isEmpty();
      evidence.addAll(prior);
    }
    Map<String, Object> features =
        highRecall
            ? Map.of(
                "groupCount",
                group.size(),
                "patternTransactionRefs",
                group.stream().map(Transfer::transactionRef).toList())
            : Map.of("groupCount", group.size());
    rules.add(rule("STRUCTURED_SPLIT", matches, tx, evidence, features));
  }

  private void rapid(Transfer tx, Context ctx, List<RuleAudit> rules) {
    Instant t = tx.transactionTime();
    if (highRecall) {
      rapidRecall(tx, ctx, rules);
      return;
    }
    if (!inbound(ctx, t.minusSeconds(config.integer("RAPID_MAX_DELAY_SECONDS")), t)) {
      rules.add(
          missing(
              "RAPID_PASS_THROUGH",
              "Requires complete "
                  + config.integer("RAPID_MAX_DELAY_SECONDS")
                  + "-second posting coverage and posting data"));
      return;
    }
    if (enhanced) {
      RuleAudit amount =
          rules.stream().filter(r -> r.ruleCode().equals("AMOUNT_SPIKE")).findFirst().orElseThrow();
      if (amount.status().equals("NOT_EVALUATED")) {
        rules.add(
            missing(
                "RAPID_PASS_THROUGH",
                "Requires complete "
                    + config.integer("BASELINE_MIN_COUNT")
                    + "-sample outbound baseline for flow anomaly"));
        return;
      }
      if (!amount.status().equals("MATCHED")) {
        rules.add(
            rule("RAPID_PASS_THROUGH", false, tx, List.of(tx), Map.of("abnormalAmount", false)));
        return;
      }
    }
    Transfer incoming =
        ctx.inbound().stream()
            .filter(
                h ->
                    h.postedAt() != null
                        && !h.postedAt()
                            .isBefore(t.minusSeconds(config.integer("RAPID_MAX_DELAY_SECONDS")))
                        && h.postedAt().isBefore(t))
            .sorted(
                Comparator.comparing(Transfer::postedAt)
                    .reversed()
                    .thenComparing(Transfer::transactionRef))
            .findFirst()
            .orElse(null);
    boolean matches = incoming != null;
    Map<String, Object> features = Map.of();
    if (incoming != null) {
      Duration delay = Duration.between(incoming.postedAt(), t);
      matches =
          delay.compareTo(Duration.ofSeconds(config.integer("RAPID_MIN_DELAY_SECONDS"))) >= 0
              && delay.compareTo(Duration.ofSeconds(config.integer("RAPID_MAX_DELAY_SECONDS"))) <= 0
              && !incoming.senderAccountId().equals(tx.receiverAccountId())
              && !incoming.senderAccountId().equals(tx.senderAccountId())
              && withinRatio(
                  tx.amountVnd(),
                  incoming.amountVnd(),
                  config.decimal("RAPID_MIN_RATIO"),
                  config.decimal("RAPID_MAX_RATIO"));
      features =
          Map.of(
              "postingDelay",
              delay.toString(),
              "inboundAmountVnd",
              incoming.amountVnd(),
              "outboundAmountVnd",
              tx.amountVnd());
    }
    rules.add(
        rule(
            "RAPID_PASS_THROUGH",
            matches,
            tx,
            incoming == null ? List.of(tx) : List.of(incoming, tx),
            features));
  }

  private void rapidRecall(Transfer tx, Context ctx, List<RuleAudit> rules) {
    Instant t = tx.transactionTime();
    if (!inbound(ctx, t.minusSeconds(config.integer("RAPID_MAX_DELAY_SECONDS")), t)) {
      rules.add(
          missing(
              "RAPID_PASS_THROUGH",
              "Requires complete "
                  + config.integer("RAPID_MAX_DELAY_SECONDS")
                  + "-second posting coverage and posting data"));
      return;
    }
    Transfer incoming =
        ctx.inbound().stream()
            .filter(
                h ->
                    h.postedAt() != null
                        && !h.postedAt()
                            .isBefore(t.minusSeconds(config.integer("RAPID_MAX_DELAY_SECONDS")))
                        && !h.postedAt()
                            .isAfter(t.minusSeconds(config.integer("RAPID_MIN_DELAY_SECONDS")))
                        && !h.senderAccountId().equals(tx.receiverAccountId())
                        && !h.senderAccountId().equals(tx.senderAccountId())
                        && withinRatio(
                            tx.amountVnd(),
                            h.amountVnd(),
                            config.decimal("RAPID_MIN_RATIO"),
                            config.decimal("RAPID_MAX_RATIO")))
            .sorted(
                Comparator.comparing(Transfer::postedAt)
                    .reversed()
                    .thenComparing(Transfer::transactionRef))
            .findFirst()
            .orElse(null);
    rules.add(
        rule(
            "RAPID_PASS_THROUGH",
            incoming != null,
            tx,
            incoming == null ? List.of(tx) : List.of(incoming, tx),
            incoming == null
                ? Map.of()
                : Map.of(
                    "postingDelay",
                    Duration.between(incoming.postedAt(), t).toString(),
                    "inboundAmountVnd",
                    incoming.amountVnd(),
                    "outboundAmountVnd",
                    tx.amountVnd())));
  }

  private void multi(Transfer tx, Context ctx, List<RuleAudit> rules) {
    Instant t = tx.transactionTime();
    int window = config.integer("MULTI_WINDOW_SECONDS");
    if (!inbound(ctx, t.minusSeconds(window), t) || !outbound(ctx, t.minusSeconds(window), t)) {
      rules.add(
          missing(
              "MULTI_ACCOUNT_FLOW",
              "Requires complete " + window + "-second inbound posting/outbound coverage"));
      return;
    }
    List<Transfer> incoming =
        ctx.inbound().stream()
            .filter(
                h ->
                    h.postedAt() != null
                        && h.postedAt().isAfter(t.minusSeconds(window))
                        && h.postedAt().isBefore(t))
            .toList();
    if (incoming.isEmpty()) {
      rules.add(rule("MULTI_ACCOUNT_FLOW", false, tx, List.of(tx), Map.of("inboundCount", 0)));
      return;
    }
    Instant last =
        incoming.stream().map(Transfer::postedAt).max(Comparator.naturalOrder()).orElseThrow();
    List<Transfer> outgoing = rolling(tx, ctx, last);
    Set<String> sources =
        incoming.stream().map(Transfer::senderAccountId).collect(Collectors.toSet());
    Set<String> targets =
        outgoing.stream().map(Transfer::receiverAccountId).collect(Collectors.toSet());
    BigDecimal in = sum(incoming);
    BigDecimal out = sum(outgoing);
    boolean matches =
        sources.size() >= config.integer("MULTI_MIN_SOURCES")
            && targets.size() >= config.integer("MULTI_MIN_TARGETS")
            && Collections.disjoint(sources, targets)
            && withinRatio(
                out, in, config.decimal("MULTI_MIN_RATIO"), config.decimal("MULTI_MAX_RATIO"));
    List<Transfer> evidence = new ArrayList<>(incoming);
    evidence.addAll(outgoing);
    rules.add(
        rule(
            "MULTI_ACCOUNT_FLOW",
            matches,
            tx,
            evidence,
            Map.of(
                "sourceCount",
                sources.size(),
                "targetCount",
                targets.size(),
                "inboundSumVnd",
                in,
                "outboundSumVnd",
                out,
                "lastPosting",
                last)));
  }

  private List<Transfer> rolling(Transfer tx, Context ctx, Instant after) {
    List<Transfer> result =
        new ArrayList<>(
            ctx.outbound().stream()
                .filter(
                    h ->
                        h.transactionTime().isAfter(after)
                            && !h.transactionTime().isAfter(tx.transactionTime()))
                .toList());
    result.add(tx);
    return result;
  }

  private BigDecimal sum(List<Transfer> transfers) {
    return transfers.stream().map(Transfer::amountVnd).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private boolean withinRatio(
      BigDecimal outbound, BigDecimal inbound, BigDecimal lower, BigDecimal upper) {
    // Cross multiplication avoids rounding a ratio across a decision boundary.
    return outbound.compareTo(inbound.multiply(lower)) >= 0
        && outbound.compareTo(inbound.multiply(upper)) <= 0;
  }

  private boolean outbound(Context ctx, Instant from, Instant through) {
    Coverage coverage = ctx.coverage();
    return coverage != null && coverage.outbound(from, through);
  }

  private boolean inbound(Context ctx, Instant from, Instant through) {
    Coverage coverage = ctx.coverage();
    return coverage != null && !ctx.unknownPosting() && coverage.inbound(from, through);
  }

  private RuleAudit missing(String code, String reason) {
    return new RuleAudit(code, "NOT_EVALUATED", reason, Map.of(), List.of(), null);
  }

  private RuleAudit rule(
      String code,
      boolean matched,
      Transfer tx,
      List<Transfer> evidence,
      Map<String, Object> features) {
    List<String> refs =
        evidence.stream().map(Transfer::transactionRef).distinct().sorted().toList();
    if (highRecall
        && Set.of("FREQUENCY_SPIKE", "RAPID_PASS_THROUGH", "MULTI_ACCOUNT_FLOW").contains(code)) {
      features = new LinkedHashMap<>(features);
      features.put("patternTransactionRefs", refs);
    }
    return new RuleAudit(
        code,
        matched ? "MATCHED" : "NOT_MATCHED",
        matched ? "Rule conditions satisfied" : "Rule conditions not satisfied",
        features,
        refs,
        matched ? eventId(code, tx.senderAccountId(), refs) : null);
  }

  private String eventId(String code, String account, List<String> refs) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      List<String> parts = new ArrayList<>(List.of(version, code, account));
      parts.addAll(refs);
      for (String part : parts) {
        byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
