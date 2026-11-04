package com.scrumai.fraud.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scrumai.common.exception.GlobalExceptionHandler;
import com.scrumai.fraud.enums.RiskLevel;
import com.scrumai.fraud.service.RiskModels.Result;
import com.scrumai.fraud.service.TransactionEvaluationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransactionEvaluationControllerTest {
  private TransactionEvaluationService service;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    service = mock(TransactionEvaluationService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new TransactionEvaluationController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void evaluatesInCommonEnvelopeAndIgnoresSuppliedScoreAndLabels() throws Exception {
    when(service.evaluate(any()))
        .thenReturn(
            new Result(
                "one",
                null,
                "INSUFFICIENT_DATA",
                0,
                RiskLevel.LOW,
                "PARTIAL",
                60,
                "rule-score-v1",
                List.of(),
                List.of()));
    mvc.perform(
            post("/api/v1/transactions/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body("100", "VND", "\"2026-10-01T08:00:00+07:00\"")
                        .replace("}", ",\"riskScore\":100,\"is_fraud\":true}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.riskScore").value(0))
        .andExpect(jsonPath("$.data.decision").value("INSUFFICIENT_DATA"));
    verify(service).evaluate(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "1.5", "10000000000000000000", "null"})
  void rejectsInvalidVndAmounts(String amount) throws Exception {
    mvc.perform(
            post("/api/v1/transactions/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(amount, "VND", "\"2026-10-01T08:00:00+07:00\"")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"2026-10-01T08:00:00\"", "1720000000", "null"})
  void requiresExplicitTimestampOffset(String timestamp) throws Exception {
    mvc.perform(
            post("/api/v1/transactions/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("100", "VND", timestamp)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void rejectsNonVndCurrency() throws Exception {
    mvc.perform(
            post("/api/v1/transactions/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("100", "USD", "\"2026-10-01T08:00:00Z\"")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void postingAlsoRequiresAStringWithExplicitOffset() throws Exception {
    mvc.perform(
            post("/api/v1/transactions/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body("100", "VND", "\"2026-10-01T08:00:00Z\"")
                        .replace("}", ",\"postedAt\":1720000000}")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  private String body(String amount, String currency, String timestamp) {
    return """
        {"transactionRef":"one","senderAccountId":"A","receiverAccountId":"B",
         "amountVnd":%s,"currency":"%s","transactionTime":%s}
        """
        .formatted(amount, currency, timestamp);
  }
}
