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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RiskScoreEngine {
  public static final String VERSION = "rule-score-v1";
  public static final int FRAUD_THRESHOLD = 60;
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

  public Map<String, Object> configuration() {
    return Map.ofEntries(
        Map.entry("ruleVersion", VERSION),
        Map.entry("fraudThreshold", FRAUD_THRESHOLD),
        Map.entry("weights", WEIGHTS),
        Map.entry("groups", GROUPS),
        Map.entry("baseline", "[t-30d,t); min 20; exact median; amount > 8*median"),
        Map.entry("hour", "Asia/Ho_Chi_Minh [02:00,04:00)"),
        Map.entry("frequency", "(t-600s,t]; count >= 12"),
        Map.entry("split", "last 9 in (t-3600s,t]; equal VND; gaps [300,420]s; prior receiver 30d"),
        Map.entry(
            "rapid", "latest posting [t-120s,t); delay [60,120]s; ratio [0.95,1]; 3 accounts"),
        Map.entry(
            "multi",
            "all inbound (t-600s,t); all outbound (max posting,t]; 5 sources; 2 targets; disjoint; ratio [0.95,1]"),
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
      String winner = matched.stream().max(Comparator.comparingInt(WEIGHTS::get)).orElse(null);
      for (String code : matched) {
        triggered.add(
            new TriggeredRule(
                code,
                code.equals(winner) ? WEIGHTS.get(code) : 0,
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
            VERSION,
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
    boolean spike = tx.amountVnd().compareTo(median.multiply(BigDecimal.valueOf(8))) > 0;
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
    rules.add(rule("NEW_BENEFICIARY_LARGE", spike && !known, tx, evidence, features));
  }

  private void frequency(Transfer tx, Context ctx, List<RuleAudit> rules) {
    Instant start = tx.transactionTime().minusSeconds(600);
    if (!outbound(ctx, start, tx.transactionTime())) {
      rules.add(missing("FREQUENCY_SPIKE", "Requires complete 600-second outbound coverage"));
      return;
    }
    List<Transfer> evidence = rolling(tx, ctx, start);
    rules.add(
        rule(
            "FREQUENCY_SPIKE",
            evidence.size() >= 12,
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
            .sorted(Comparator.comparing(Transfer::transactionTime))
            .toList();
    List<Transfer> group = pair.subList(Math.max(0, pair.size() - 9), pair.size());
    boolean matches = group.size() == 9;
    for (int i = 0; matches && i < group.size(); i++) {
      matches = group.get(i).amountVnd().compareTo(tx.amountVnd()) == 0;
      if (i > 0) {
        Duration gap =
            Duration.between(group.get(i - 1).transactionTime(), group.get(i).transactionTime());
        matches &=
            gap.compareTo(Duration.ofSeconds(300)) >= 0
                && gap.compareTo(Duration.ofSeconds(420)) <= 0;
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
    rules.add(rule("STRUCTURED_SPLIT", matches, tx, evidence, Map.of("groupCount", group.size())));
  }

  private void rapid(Transfer tx, Context ctx, List<RuleAudit> rules) {
    Instant t = tx.transactionTime();
    if (!inbound(ctx, t.minusSeconds(120), t)) {
      rules.add(
          missing(
              "RAPID_PASS_THROUGH",
              "Requires complete 120-second posting coverage and posting data"));
      return;
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

  private void multi(Transfer tx, Context ctx, List<RuleAudit> rules) {
    Instant t = tx.transactionTime();
    if (!inbound(ctx, t.minusSeconds(600), t) || !outbound(ctx, t.minusSeconds(600), t)) {
      rules.add(
          missing(
              "MULTI_ACCOUNT_FLOW",
              "Requires complete 600-second inbound posting/outbound coverage"));
      return;
    }
    List<Transfer> incoming =
        ctx.inbound().stream()
            .filter(
                h ->
                    h.postedAt() != null
                        && h.postedAt().isAfter(t.minusSeconds(600))
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
        sources.size() >= 5
            && targets.size() >= 2
            && Collections.disjoint(sources, targets)
            && ratio(out, in);
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
      List<String> parts = new ArrayList<>(List.of(VERSION, code, account));
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
