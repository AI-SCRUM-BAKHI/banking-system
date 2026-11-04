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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RiskScoreEngine {
  public static final String VERSION = "rule-score-v1";
  public static final String V2 = "rule-score-v2";
  public static final String V3_RECALL = "rule-score-v3-recall";
  public static final int FRAUD_THRESHOLD = 60;
  // Repositories load this superset; each profile still applies its own exact window.
  public static final int FLOW_CONTEXT_SECONDS = 1800;
  private static final Duration MONTH = Duration.ofDays(30);
  private static final BigDecimal LOWER_RATIO = new BigDecimal("0.95");
  private static final Map<String, Integer> WEIGHTS =
      Map.of(
          "AMOUNT_SPIKE", 35,
          "NEW_BENEFICIARY_LARGE", 50,
          "UNUSUAL_HOUR", 25,
          "FREQUENCY_SPIKE", 45,
          "STRUCTURED_SPLIT", 55,
          "RAPID_PASS_THROUGH", 65,
          "MULTI_ACCOUNT_FLOW", 80);
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

  public RiskScoreEngine() {
    this(VERSION);
  }

  @Autowired
  public RiskScoreEngine(@Value("${app.fraud.rules.version:rule-score-v1}") String version) {
    if (!VERSION.equals(version) && !V2.equals(version) && !V3_RECALL.equals(version)) {
      throw new IllegalArgumentException("Unsupported fraud rule version");
    }
    this.version = version;
    enhanced = !VERSION.equals(version);
    highRecall = V3_RECALL.equals(version);
    weights =
        enhanced
            ? Map.of(
                "AMOUNT_SPIKE", 60,
                "NEW_BENEFICIARY_LARGE", 65,
                "UNUSUAL_HOUR", highRecall ? 60 : 25,
                "FREQUENCY_SPIKE", 65,
                "STRUCTURED_SPLIT", 70,
                "RAPID_PASS_THROUGH", highRecall ? 65 : 75,
                "MULTI_ACCOUNT_FLOW", 80)
            : WEIGHTS;
  }

  public Map<String, Object> configuration() {
    return Map.ofEntries(
        Map.entry("ruleVersion", version),
        Map.entry("fraudThreshold", FRAUD_THRESHOLD),
        Map.entry("weights", weights),
        Map.entry("groups", GROUPS),
        Map.entry(
            "baseline",
            highRecall
                ? "[t-30d,t); min 20; exact median; amount > 4*median; new beneficiary > 2.5*median"
                : "[t-30d,t); min 20; exact median; amount > 8*median"),
        Map.entry("hour", "Asia/Ho_Chi_Minh [02:00,04:00)"),
        Map.entry(
            "frequency",
            enhanced ? "(t-120s,t]; count >= " + (highRecall ? 2 : 3) : "(t-600s,t]; count >= 12"),
        Map.entry(
            "split",
            highRecall
                ? "last 2 same-pair equal-VND transfers in (t-3600s,t]; gaps [120,900]s; prior receiver 30d"
                : enhanced
                    ? "last 3 in (t-3600s,t]; equal VND; gaps [240,480]s; prior receiver 30d"
                    : "last 9 in (t-3600s,t]; equal VND; gaps [300,420]s; prior receiver 30d"),
        Map.entry(
            "rapid",
            highRecall
                ? "latest valid posting [t-600s,t); delay [30,600]s; ratio [0.8,1.2]; 3 accounts"
                : "latest posting [t-120s,t); delay [60,120]s; ratio [0.95,1]; 3 accounts"
                    + (enhanced ? "; complete 20-sample baseline; outbound > 8*median" : "")),
        Map.entry(
            "multi",
            "all inbound (t-"
                + (enhanced ? 1800 : 600)
                + (highRecall
                    ? "s,t); all outbound (max posting,t]; 3 sources; 1 target; disjoint; ratio [0.4,1.2]"
                    : "s,t); all outbound (max posting,t]; 5 sources; 2 targets; disjoint; ratio [0.95,1]")),
        Map.entry("score", "sum group maxima; cap 100; levels 30/60/80"));
  }

  public Evaluation evaluate(Transfer current, Context context) {
    List<RuleAudit> rules = new ArrayList<>();
    amount(current, context, rules);
    int hour = current.transactionTime().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).getHour();
    rules.add(
        rule(
            "UNUSUAL_HOUR",
            hour >= 2 && hour < 4,
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
        score >= FRAUD_THRESHOLD ? Boolean.TRUE : missing.isEmpty() ? Boolean.FALSE : null;
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
            FRAUD_THRESHOLD,
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
                    !h.transactionTime().isBefore(t.minus(MONTH))
                        && h.transactionTime().isBefore(t))
            .toList();
    if (!outbound(ctx, t.minus(MONTH), t) || baseline.size() < 20) {
      String reason = "Requires complete outbound coverage and at least 20 baseline samples";
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
        tx.amountVnd().compareTo(median.multiply(BigDecimal.valueOf(highRecall ? 4 : 8))) > 0;
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
        highRecall ? tx.amountVnd().compareTo(median.multiply(new BigDecimal("2.5"))) > 0 : spike;
    rules.add(rule("NEW_BENEFICIARY_LARGE", newLarge && !known, tx, evidence, features));
  }

  private void frequency(Transfer tx, Context ctx, List<RuleAudit> rules) {
    int window = enhanced ? 120 : 600;
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
            evidence.size() >= (highRecall ? 2 : enhanced ? 3 : 12),
            tx,
            evidence,
            Map.of("outboundCount", evidence.size())));
  }

  private void split(Transfer tx, Context ctx, List<RuleAudit> rules) {
    Instant t = tx.transactionTime();
    if (!outbound(ctx, t.minusSeconds(3600), t)) {
      rules.add(missing("STRUCTURED_SPLIT", "Requires complete one-hour outbound coverage"));
      return;
    }
    // List order is ingest order; stable sorting preserves it for equal timestamps.
    List<Transfer> pair =
        rolling(tx, ctx, t.minusSeconds(3600)).stream()
            .filter(h -> h.receiverAccountId().equals(tx.receiverAccountId()))
            .filter(h -> !highRecall || h.amountVnd().compareTo(tx.amountVnd()) == 0)
            .sorted(Comparator.comparing(Transfer::transactionTime))
            .toList();
    int required = highRecall ? 2 : enhanced ? 3 : 9;
    List<Transfer> group = pair.subList(Math.max(0, pair.size() - required), pair.size());
    boolean matches = group.size() == required;
    for (int i = 0; matches && i < group.size(); i++) {
      matches = group.get(i).amountVnd().compareTo(tx.amountVnd()) == 0;
      if (i > 0) {
        Duration gap =
            Duration.between(group.get(i - 1).transactionTime(), group.get(i).transactionTime());
        matches &=
            gap.compareTo(Duration.ofSeconds(highRecall ? 120 : enhanced ? 240 : 300)) >= 0
                && gap.compareTo(Duration.ofSeconds(highRecall ? 900 : enhanced ? 480 : 420)) <= 0;
      }
    }
    List<Transfer> evidence = new ArrayList<>(group);
    if (matches) {
      Instant first = group.getFirst().transactionTime();
      if (!outbound(ctx, first.minus(MONTH), first)) {
        rules.add(
            missing("STRUCTURED_SPLIT", "Requires receiver history coverage before first split"));
        return;
      }
      List<Transfer> prior =
          ctx.outbound().stream()
              .filter(
                  h ->
                      h.receiverAccountId().equals(tx.receiverAccountId())
                          && !h.transactionTime().isBefore(first.minus(MONTH))
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
    if (!inbound(ctx, t.minusSeconds(120), t)) {
      rules.add(
          missing(
              "RAPID_PASS_THROUGH",
              "Requires complete 120-second posting coverage and posting data"));
      return;
    }
    if (enhanced) {
      RuleAudit amount =
          rules.stream().filter(r -> r.ruleCode().equals("AMOUNT_SPIKE")).findFirst().orElseThrow();
      if (amount.status().equals("NOT_EVALUATED")) {
        rules.add(
            missing(
                "RAPID_PASS_THROUGH",
                "Requires complete 20-sample outbound baseline for flow anomaly"));
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
                        && !h.postedAt().isBefore(t.minusSeconds(120))
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
          delay.compareTo(Duration.ofSeconds(60)) >= 0
              && delay.compareTo(Duration.ofSeconds(120)) <= 0
              && !incoming.senderAccountId().equals(tx.receiverAccountId())
              && !incoming.senderAccountId().equals(tx.senderAccountId())
              && ratio(tx.amountVnd(), incoming.amountVnd());
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
    if (!inbound(ctx, t.minusSeconds(600), t)) {
      rules.add(
          missing(
              "RAPID_PASS_THROUGH",
              "Requires complete 600-second posting coverage and posting data"));
      return;
    }
    Transfer incoming =
        ctx.inbound().stream()
            .filter(
                h ->
                    h.postedAt() != null
                        && !h.postedAt().isBefore(t.minusSeconds(600))
                        && !h.postedAt().isAfter(t.minusSeconds(30))
                        && !h.senderAccountId().equals(tx.receiverAccountId())
                        && !h.senderAccountId().equals(tx.senderAccountId())
                        && withinRatio(tx.amountVnd(), h.amountVnd(), "0.8", "1.2"))
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
    int window = enhanced ? FLOW_CONTEXT_SECONDS : 600;
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
        sources.size() >= (highRecall ? 3 : 5)
            && targets.size() >= (highRecall ? 1 : 2)
            && Collections.disjoint(sources, targets)
            && (highRecall ? withinRatio(out, in, "0.4", "1.2") : ratio(out, in));
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

  private boolean ratio(BigDecimal outbound, BigDecimal inbound) {
    // Cross multiplication avoids rounding a ratio across a decision boundary.
    return outbound.compareTo(inbound.multiply(LOWER_RATIO)) >= 0
        && outbound.compareTo(inbound) <= 0;
  }

  private boolean withinRatio(BigDecimal outbound, BigDecimal inbound, String lower, String upper) {
    return outbound.compareTo(inbound.multiply(new BigDecimal(lower))) >= 0
        && outbound.compareTo(inbound.multiply(new BigDecimal(upper))) <= 0;
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
