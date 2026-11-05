package com.scrumai.fraud.service;

import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.fraud.dto.AiAnalysis;
import com.scrumai.fraud.repository.AiAnalysisRepository;
import com.scrumai.fraud.repository.AiAnalysisRepository.Snapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiAnalysisService {
  private static final String PROMPT_VERSION = "fraud-analysis-v1";
  private final AiAnalysisRepository repository;
  private final OpenRouterClient client;
  private final ObjectMapper mapper;
  // Bounded locks prevent simultaneous calls for the same ref in this JVM.
  private final Object[] locks = new Object[64];

  public AiAnalysisService(
      AiAnalysisRepository repository, OpenRouterClient client, ObjectMapper mapper) {
    this.repository = repository;
    this.client = client;
    this.mapper = mapper;
    for (int i = 0; i < locks.length; i++) {
      locks[i] = new Object();
    }
  }

  public AiAnalysis get(String ref) {
    snapshot(ref);
    return repository
        .find(ref)
        .map(json -> mapper.readValue(json, AiAnalysis.class))
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.AI_ANALYSIS_NOT_FOUND,
                    "AI analysis has not been created for this transaction"));
  }

  public AiAnalysis create(String ref) {
    synchronized (locks[Math.floorMod(ref.hashCode(), locks.length)]) {
      Snapshot snapshot = snapshot(ref);
      var result = mapper.readTree(snapshot.resultJson());
      if (!"FRAUD".equals(result.path("decision").asText())) {
        throw new BusinessException(
            ErrorCode.AI_ANALYSIS_NOT_ELIGIBLE,
            "Only transactions evaluated with decision FRAUD can be analyzed");
      }
      var cached = repository.find(ref);
      if (cached.isPresent()) {
        return mapper.readValue(cached.get(), AiAnalysis.class);
      }
      var audit = mapper.readTree(snapshot.auditJson());
      Set<String> allowedRefs = new HashSet<>();
      allowedRefs.add(ref);
      Map<String, Set<String>> ruleEvidence = new HashMap<>();
      for (var rule : audit.path("rules")) {
        if ("MATCHED".equals(rule.path("status").asText())) {
          Set<String> refs = new HashSet<>();
          rule.path("evidence").forEach(node -> refs.add(node.asText()));
          ruleEvidence.put(rule.path("ruleCode").asText(), refs);
          allowedRefs.addAll(refs);
        }
      }
      Map<String, Object> inputData = new LinkedHashMap<>();
      inputData.put("transaction", mapper.readTree(snapshot.requestJson()));
      inputData.put("evaluation", result);
      inputData.put("audit", audit);
      String input = mapper.writeValueAsString(inputData);
      if (ruleEvidence.isEmpty() || input.length() > 150_000) {
        throw new BusinessException(
            ErrorCode.AI_ANALYSIS_NOT_ELIGIBLE,
            "Transaction evidence is missing or too large for AI analysis");
      }
      AiAnalysis analysis = client.analyze(input);
      validate(analysis, ruleEvidence, allowedRefs);
      Set<String> explained = new HashSet<>();
      analysis.fraudExplanation().reasons().forEach(reason -> explained.add(reason.ruleCode()));
      for (var rule : result.path("triggeredRules")) {
        if (rule.path("scoreContribution").asInt() > 0
            && !explained.contains(rule.path("ruleCode").asText())) {
          throw OpenRouterClient.invalidResponse();
        }
      }
      repository.save(
          ref, mapper.writeValueAsString(analysis), hash(input), client.model(), PROMPT_VERSION);
      // Across JVMs the unique constraint selects one immutable winning result.
      return get(ref);
    }
  }

  private Snapshot snapshot(String ref) {
    if (ref == null || ref.isBlank() || ref.length() > 80) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid transaction reference");
    }
    return repository
        .snapshot(ref)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND, "Evaluated transaction not found"));
  }

  private static void validate(
      AiAnalysis analysis, Map<String, Set<String>> rules, Set<String> allowedRefs) {
    if (analysis == null || analysis.fraudExplanation() == null) {
      throw OpenRouterClient.invalidResponse();
    }
    text(analysis.fraudExplanation().summary(), 2000);
    list(analysis.fraudExplanation().reasons(), 5);
    Set<String> seenRules = new HashSet<>();
    for (var reason : analysis.fraudExplanation().reasons()) {
      if (reason == null
          || !rules.containsKey(reason.ruleCode())
          || !seenRules.add(reason.ruleCode())) {
        throw OpenRouterClient.invalidResponse();
      }
      text(reason.description(), 2000);
      refs(reason.evidenceTransactionRefs(), rules.get(reason.ruleCode()));
    }
    list(analysis.investigationRecommendations(), 5);
    Set<Integer> priorities = new HashSet<>();
    for (var step : analysis.investigationRecommendations()) {
      if (step == null
          || step.priority() < 1
          || step.priority() > 5
          || !priorities.add(step.priority())) {
        throw OpenRouterClient.invalidResponse();
      }
      text(step.action(), 1000);
      text(step.rationale(), 2000);
      refs(step.evidenceTransactionRefs(), allowedRefs);
      list(step.dataToCollect(), 8);
      step.dataToCollect().forEach(item -> text(item, 500));
    }
  }

  private static void refs(List<String> refs, Set<String> allowed) {
    list(refs, 100);
    if (refs.stream().anyMatch(ref -> ref == null || !allowed.contains(ref))) {
      throw OpenRouterClient.invalidResponse();
    }
  }

  private static void text(String value, int maximum) {
    if (value == null || value.isBlank() || value.length() > maximum) {
      throw OpenRouterClient.invalidResponse();
    }
  }

  private static void list(List<?> values, int maximum) {
    if (values == null || values.isEmpty() || values.size() > maximum) {
      throw OpenRouterClient.invalidResponse();
    }
  }

  private static String hash(String input) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
