package com.scrumai.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
  INVALID_REQUEST(HttpStatus.BAD_REQUEST),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
  RESOURCE_CONFLICT(HttpStatus.CONFLICT),
  AI_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND),
  AI_ANALYSIS_NOT_ELIGIBLE(HttpStatus.CONFLICT),
  AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
  AI_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY),
  AI_PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
  ACCESS_DENIED(HttpStatus.FORBIDDEN),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
