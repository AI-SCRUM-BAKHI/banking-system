package com.scrumai.common.api;

import java.time.Instant;

public record ApiResponse<T>(
    boolean success, T data, String message, Instant timestamp, String requestId) {

  public static <T> ApiResponse<T> success(T data) {
    return success(data, "Request completed successfully");
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, data, message, Instant.now(), RequestId.current());
  }
}
