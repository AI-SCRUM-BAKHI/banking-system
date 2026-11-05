package com.scrumai.fraud.controller;

import com.scrumai.common.api.ApiResponse;
import com.scrumai.fraud.dto.AiAnalysis;
import com.scrumai.fraud.service.AiAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions/{transactionRef}/ai-analysis")
public class AiAnalysisController {
  private final AiAnalysisService service;

  public AiAnalysisController(AiAnalysisService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<AiAnalysis> get(@PathVariable String transactionRef) {
    return ApiResponse.success(service.get(transactionRef));
  }

  @PostMapping
  public ApiResponse<AiAnalysis> create(@PathVariable String transactionRef) {
    return ApiResponse.success(service.create(transactionRef));
  }
}
