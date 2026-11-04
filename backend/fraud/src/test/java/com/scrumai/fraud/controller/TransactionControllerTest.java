package com.scrumai.fraud.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.common.exception.GlobalExceptionHandler;
import com.scrumai.fraud.service.TransactionIngestService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransactionControllerTest {
  private TransactionIngestService service;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    service = mock(TransactionIngestService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new TransactionController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void returnsCreatedPendingTransaction() throws Exception {
    when(service.create(any()))
        .thenReturn(
            new TransactionIngestService.Created(
                "source-one", "PENDING", Instant.parse("2026-10-01T08:00:00Z")));
    mvc.perform(
            post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("100", "\"2026-10-01T08:00:00Z\"")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.transactionRef").value("source-one"))
        .andExpect(jsonPath("$.data.processingStatus").value("PENDING"))
        .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "1000000000000000", "null"})
  void rejectsInvalidAmounts(String amount) throws Exception {
    mvc.perform(
            post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(amount, "\"2026-10-01T08:00:00Z\"")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"2026-10-01T08:00:00\"", "1720000000", "null"})
  void requiresExplicitTimestampOffset(String time) throws Exception {
    mvc.perform(
            post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("100", time)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void rejectsMissingMaskAndUnsupportedCurrency() throws Exception {
    for (String payload :
        new String[] {
          body("100", "\"2026-10-01T08:00:00Z\"").replace("\"VND\"", "\"USD\""),
          body("100", "\"2026-10-01T08:00:00Z\"").replace("\"****1\"", "\"\"")
        }) {
      mvc.perform(
              post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(payload))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(service);
  }

  @Test
  void duplicateReferenceReturnsConflict() throws Exception {
    when(service.create(any()))
        .thenThrow(
            new BusinessException(
                ErrorCode.RESOURCE_CONFLICT, "Transaction reference already exists"));
    mvc.perform(
            post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("100", "\"2026-10-01T08:00:00Z\"")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_CONFLICT"));
  }

  private String body(String amount, String time) {
    return """
        {"transactionRef":"source-one","fromAccountRef":"A","fromAccountMasked":"****1",
         "toAccountRef":"B","toAccountMasked":"****2","amount":%s,
         "currency":"VND","transactionTime":%s}
        """
        .formatted(amount, time);
  }
}
