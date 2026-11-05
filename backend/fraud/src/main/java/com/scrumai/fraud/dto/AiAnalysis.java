package com.scrumai.fraud.dto;

import java.util.List;

public record AiAnalysis(
    Explanation fraudExplanation, List<Recommendation> investigationRecommendations) {
  public record Explanation(String summary, List<Reason> reasons) {}

  public record Reason(String ruleCode, String description, List<String> evidenceTransactionRefs) {}

  public record Recommendation(
      int priority,
      String action,
      String rationale,
      List<String> evidenceTransactionRefs,
      List<String> dataToCollect) {}
}
