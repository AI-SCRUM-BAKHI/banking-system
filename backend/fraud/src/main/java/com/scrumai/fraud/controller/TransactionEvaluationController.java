package com.scrumai.fraud.controller;

import com.scrumai.common.api.ApiResponse;
import com.scrumai.fraud.dto.EvaluationRequest;
import com.scrumai.fraud.service.RiskModels.Result;
import com.scrumai.fraud.service.TransactionEvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionEvaluationController {
  private final TransactionEvaluationService service;

  public TransactionEvaluationController(TransactionEvaluationService service) {
    this.service = service;
  }

  @PostMapping("/evaluate")
  public ApiResponse<Result> evaluate(@Valid @RequestBody EvaluationRequest request) {
    return ApiResponse.success(service.evaluate(request));
  }
}
