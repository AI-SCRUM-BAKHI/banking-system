package com.scrumai.fraud.controller;

import com.scrumai.common.api.ApiResponse;
import com.scrumai.fraud.service.IpIntelligenceService;
import com.scrumai.fraud.service.IpIntelligenceService.Insights;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IpIntelligenceController {
  private final IpIntelligenceService service;

  public IpIntelligenceController(IpIntelligenceService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/fraud-transactions/ip-intelligence")
  public ApiResponse<Insights> load() {
    return ApiResponse.success(service.load());
  }
}
