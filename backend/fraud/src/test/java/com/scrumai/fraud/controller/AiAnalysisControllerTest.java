package com.scrumai.fraud.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.common.exception.GlobalExceptionHandler;
import com.scrumai.fraud.dto.AiAnalysis;
import com.scrumai.fraud.service.AiAnalysisService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiAnalysisControllerTest {
  private AiAnalysisService service;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    service = mock(AiAnalysisService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new AiAnalysisController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void postReturnsTwoSectionsInCommonEnvelope() throws Exception {
    when(service.create("TX-1"))
        .thenReturn(
            new AiAnalysis(
                new AiAnalysis.Explanation(
                    "Explanation",
                    List.of(new AiAnalysis.Reason("UNUSUAL_HOUR", "Reason", List.of("TX-1")))),
                List.of(
                    new AiAnalysis.Recommendation(
                        1, "Investigate", "Rationale", List.of("TX-1"), List.of("Documents")))));
    mvc.perform(post("/api/v1/transactions/TX-1/ai-analysis"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fraudExplanation.summary").value("Explanation"))
        .andExpect(jsonPath("$.data.investigationRecommendations[0].action").value("Investigate"));
  }

  @Test
  void getDistinguishesMissingAnalysis() throws Exception {
    when(service.get("TX-1"))
        .thenThrow(new BusinessException(ErrorCode.AI_ANALYSIS_NOT_FOUND, "Analysis missing"));
    mvc.perform(get("/api/v1/transactions/TX-1/ai-analysis"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("AI_ANALYSIS_NOT_FOUND"));
  }
}
