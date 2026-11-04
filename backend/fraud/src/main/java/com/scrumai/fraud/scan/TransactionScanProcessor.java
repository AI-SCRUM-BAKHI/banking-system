package com.scrumai.fraud.scan;

import com.scrumai.fraud.dto.FraudRequests.RuleHit;
import com.scrumai.fraud.service.RiskScoreEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class TransactionScanProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionScanProcessor.class);
  private final TransactionScanRepository repository;
  private final RiskScoreEngine engine;
  private final ObjectMapper mapper;
  private final TransactionTemplate transaction;

  public TransactionScanProcessor(
      TransactionScanRepository repository,
      RiskScoreEngine engine,
      ObjectMapper mapper,
      PlatformTransactionManager manager) {
    this.repository = repository;
    this.engine = engine;
    this.mapper = mapper;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    transaction.setTimeout(60);
  }

  public String process(String ref) {
    try {
      String status = transaction.execute(ignored -> evaluate(ref));
      LOG.info("Transaction scan ref={} status={}", ref, status);
      return status;
    } catch (RuntimeException exception) {
      boolean temporary =
          exception instanceof TransientDataAccessException
              || exception instanceof DataAccessResourceFailureException
              || exception instanceof TransactionException;
      // Do not persist driver messages: they may contain the full source row or credentials.
      String error =
          exception instanceof IllegalArgumentException
              ? "Invalid source transaction or history; check v1 limits and timestamps"
              : "Evaluation failed: " + exception.getClass().getSimpleName();
      String status =
          transaction.execute(
              ignored -> repository.recordFailure(ref, temporary, error, Instant.now()));
      LOG.warn(
          "Transaction scan ref={} status={} errorType={}",
          ref,
          status,
          exception.getClass().getSimpleName());
      return status;
    }
  }

  private String evaluate(String ref) {
    var candidate = repository.lock(ref);
    if (candidate.isEmpty()) {
      return "SKIPPED";
    }
    SourceTransaction tx = candidate.get();
    Instant now = Instant.now();
    tx.validate(now);
    var context = repository.context(tx);
    var evaluation = engine.evaluate(tx.transfer(), context);
    List<RuleHit> hits =
        evaluation.result().triggeredRules().stream()
            .map(
                hit -> {
                  var audit =
                      evaluation.rules().stream()
                          .filter(rule -> rule.ruleCode().equals(hit.ruleCode()))
                          .findFirst()
                          .orElseThrow();
                  String explanation =
                      audit.reason()
                          + (hit.suppressedBy() == null
                              ? ""
                              : "; score suppressed by " + hit.suppressedBy());
                  return new RuleHit(
                      hit.ruleCode(),
                      hit.ruleCode(),
                      BigDecimal.valueOf(hit.scoreContribution()),
                      truncate(explanation));
                })
            .toList();
    String reason =
        hits.isEmpty()
            ? evaluation.result().decision()
            : truncate(String.join(", ", hits.stream().map(RuleHit::ruleCode).toList()));
    Map<String, Object> audit = new LinkedHashMap<>();
    audit.put("evaluatedAt", now);
    audit.put("configuration", engine.configuration());
    audit.put("coverage", context.coverage());
    audit.put("unknownPosting", context.unknownPosting());
    audit.put("contextSource", "transactions");
    audit.put("contextOrdering", "transaction_time, transaction_ref");
    audit.put("rules", evaluation.rules());
    audit.put("result", evaluation.result());
    repository.save(tx, evaluation, now, hits, reason, mapper.writeValueAsString(audit));
    return "COMPLETED";
  }

  private static String truncate(String value) {
    return value.length() <= 500 ? value : value.substring(0, 500);
  }
}
