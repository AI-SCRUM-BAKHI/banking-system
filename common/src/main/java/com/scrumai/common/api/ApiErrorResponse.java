package com.scrumai.common.api;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
    boolean success,
    ErrorBody error,
    int statusCode,
    Instant timestamp,
    String path,
    String requestId) {

  public record ErrorBody(String code, String message, Map<String, Object> details) {}

  public static ApiErrorResponse of(
      String code, String message, int statusCode, String path, Map<String, Object> details) {
    return new ApiErrorResponse(
        false,
        new ErrorBody(code, message, details),
        statusCode,
        Instant.now(),
        path,
        RequestId.current());
  }
}
