package com.scrumai.common.exception;

import com.scrumai.common.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiErrorResponse> handleBusiness(
      BusinessException exception, HttpServletRequest request) {
    ErrorCode code = exception.errorCode();
    return response(code, exception.getMessage(), request, exception.details());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    Map<String, Object> details = new LinkedHashMap<>();
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
      details.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    return response(ErrorCode.VALIDATION_ERROR, "Request validation failed", request, details);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiErrorResponse> handleBadRequest(
      Exception exception, HttpServletRequest request) {
    return response(ErrorCode.INVALID_REQUEST, "Invalid request", request, Map.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(
      Exception exception, HttpServletRequest request) {
    return response(
        ErrorCode.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, Map.of());
  }

  private ResponseEntity<ApiErrorResponse> response(
      ErrorCode code, String message, HttpServletRequest request, Map<String, Object> details) {
    return ResponseEntity.status(code.status())
        .body(
            ApiErrorResponse.of(
                code.name(), message, code.status().value(), request.getRequestURI(), details));
  }
}
