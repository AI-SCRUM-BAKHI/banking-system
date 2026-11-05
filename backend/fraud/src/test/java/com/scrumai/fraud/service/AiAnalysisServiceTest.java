package com.scrumai.fraud.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.fraud.dto.AiAnalysis;
import com.scrumai.fraud.repository.AiAnalysisRepository;
import com.scrumai.fraud.repository.AiAnalysisRepository.Snapshot;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AiAnalysisServiceTest {
  private final AiAnalysisRepository repository = mock(AiAnalysisRepository.class);
  private final OpenRouterClient client = mock(OpenRouterClient.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final AiAnalysisService service = new AiAnalysisService(repository, client, mapper);

  @BeforeEach
  void setup() {
    when(repository.snapshot("TX-1")).thenReturn(Optional.of(snapshot("FRAUD")));
    when(repository.find("TX-1")).thenReturn(Optional.empty());
  }

  @Test
  void distinguishesMissingEvaluationFromMissingAnalysis() {
    when(repository.snapshot("unknown")).thenReturn(Optional.empty());
    assertEquals(
        ErrorCode.RESOURCE_NOT_FOUND,
        assertThrows(BusinessException.class, () -> service.get("unknown")).errorCode());
    assertEquals(
        ErrorCode.AI_ANALYSIS_NOT_FOUND,
        assertThrows(BusinessException.class, () -> service.get("TX-1")).errorCode());
    verifyNoInteractions(client);
  }

  @Test
  void rejectsNonFraudWithoutCallingProvider() {
    when(repository.snapshot("TX-1")).thenReturn(Optional.of(snapshot("INSUFFICIENT_DATA")));
    assertEquals(
        ErrorCode.AI_ANALYSIS_NOT_ELIGIBLE,
        assertThrows(BusinessException.class, () -> service.create("TX-1")).errorCode());
    verifyNoInteractions(client);
  }

  @Test
  void returnsCachedAnalysisWithoutCallingProvider() {
    AiAnalysis result = analysis("TX-1", "UNUSUAL_HOUR");
    when(repository.find("TX-1")).thenReturn(Optional.of(mapper.writeValueAsString(result)));
    assertEquals(result, service.create("TX-1"));
    verifyNoInteractions(client);
  }

  @Test
  void validatesAndPersistsGroundedAnalysis() {
    AiAnalysis result = analysis("TX-1", "UNUSUAL_HOUR");
    when(client.analyze(anyString())).thenReturn(result);
    when(client.model()).thenReturn("test/model");
    when(repository.find("TX-1"))
        .thenReturn(Optional.empty(), Optional.of(mapper.writeValueAsString(result)));
    assertEquals(result, service.create("TX-1"));
    verify(repository)
        .save(eq("TX-1"), anyString(), anyString(), eq("test/model"), eq("fraud-analysis-v1"));
  }

  @Test
  void rejectsInventedEvidenceAndDoesNotPersist() {
    when(client.analyze(anyString())).thenReturn(analysis("INVENTED", "UNUSUAL_HOUR"));
    assertEquals(
        ErrorCode.AI_PROVIDER_ERROR,
        assertThrows(BusinessException.class, () -> service.create("TX-1")).errorCode());
    verify(repository, never())
        .save(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void rejectsInventedRuleAndDoesNotPersist() {
    when(client.analyze(anyString())).thenReturn(analysis("TX-1", "MADE_UP"));
    assertEquals(
        ErrorCode.AI_PROVIDER_ERROR,
        assertThrows(BusinessException.class, () -> service.create("TX-1")).errorCode());
    verify(repository, never())
        .save(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void rejectsExplanationThatOmitsTheRuleDrivingFraud() {
    when(repository.snapshot("TX-1"))
        .thenReturn(
            Optional.of(
                new Snapshot(
                    "{\"transactionRef\":\"TX-1\"}",
                    """
        {"decision":"FRAUD","triggeredRules":[
          {"ruleCode":"UNUSUAL_HOUR","scoreContribution":25},
          {"ruleCode":"FREQUENCY_SPIKE","scoreContribution":45}]}
        """,
                    """
        {"rules":[
          {"ruleCode":"UNUSUAL_HOUR","status":"MATCHED","evidence":["TX-1"]},
          {"ruleCode":"FREQUENCY_SPIKE","status":"MATCHED","evidence":["TX-1"]}]}
        """)));
    when(client.analyze(anyString())).thenReturn(analysis("TX-1", "UNUSUAL_HOUR"));
    assertEquals(
        ErrorCode.AI_PROVIDER_ERROR,
        assertThrows(BusinessException.class, () -> service.create("TX-1")).errorCode());
    verify(repository, never())
        .save(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  private static Snapshot snapshot(String decision) {
    return new Snapshot(
        "{\"transactionRef\":\"TX-1\"}",
        "{\"decision\":\"" + decision + "\"}",
        """
        {"rules":[{"ruleCode":"UNUSUAL_HOUR","status":"MATCHED","evidence":["TX-1"]}]}
        """);
  }

  static AiAnalysis analysis(String ref, String rule) {
    return new AiAnalysis(
        new AiAnalysis.Explanation(
            "Flagged by rules",
            List.of(new AiAnalysis.Reason(rule, "Unusual activity", List.of(ref)))),
        List.of(
            new AiAnalysis.Recommendation(
                1,
                "Verify purpose",
                "Unusual activity",
                List.of(ref),
                List.of("Supporting documents"))));
  }
}
