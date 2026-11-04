package com.scrumai.fraud.controller;

import com.scrumai.common.api.ApiResponse;
import com.scrumai.fraud.dto.TransactionCreateRequest;
import com.scrumai.fraud.service.TransactionIngestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
  private final TransactionIngestService service;

  public TransactionController(TransactionIngestService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<TransactionIngestService.Created>> create(
      @Valid @RequestBody TransactionCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(service.create(request)));
  }
}
