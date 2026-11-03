package com.scrumai.fraud.controller;

import com.scrumai.common.api.ApiResponse;
import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.fraud.dto.FraudRequests;
import com.scrumai.fraud.dto.FraudResponses;
import com.scrumai.fraud.enums.ReviewStatus;
import com.scrumai.fraud.enums.RiskLevel;
import com.scrumai.fraud.service.FraudTransactionService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fraud-transactions")
public class FraudTransactionController {

  private final FraudTransactionService service;

  public FraudTransactionController(FraudTransactionService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<FraudResponses.Created>> create(
      @Valid @RequestBody FraudRequests.Create request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(service.create(request)));
  }

  @GetMapping
  public ApiResponse<FraudResponses.Page<FraudResponses.SummaryItem>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) RiskLevel riskLevel,
      @RequestParam(required = false) ReviewStatus reviewStatus,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    String[] parts = sort.split(",", -1);
    if (parts.length != 2
        || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc"))) {
      throw new BusinessException(
          ErrorCode.INVALID_REQUEST, "sort must be field,asc or field,desc");
    }
    return ApiResponse.success(
        service.list(
            page,
            size,
            riskLevel,
            reviewStatus,
            keyword,
            from,
            to,
            parts[0],
            Sort.Direction.fromString(parts[1])));
  }

  @GetMapping("/summary")
  public ApiResponse<FraudResponses.Dashboard> summary(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    return ApiResponse.success(service.summary(from, to));
  }

  @GetMapping("/{id}")
  public ApiResponse<FraudResponses.Detail> detail(@PathVariable UUID id) {
    return ApiResponse.success(service.detail(id));
  }

  @GetMapping("/{id}/rule-hits")
  public ApiResponse<List<FraudResponses.RuleHit>> ruleHits(@PathVariable UUID id) {
    return ApiResponse.success(service.ruleHits(id));
  }

  @GetMapping("/{id}/graph")
  public ApiResponse<FraudResponses.Graph> graph(@PathVariable UUID id) {
    return ApiResponse.success(service.graph(id));
  }

  @PatchMapping("/{id}/review-status")
  public ApiResponse<FraudResponses.ReviewResult> review(
      @PathVariable UUID id, @Valid @RequestBody FraudRequests.Review request) {
    return ApiResponse.success(service.review(id, request.status()));
  }
}
