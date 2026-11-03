package com.scrumai.common.validation;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.validation.BindingResult;

public final class ValidationErrorMapper {
  private ValidationErrorMapper() {}

  public static Map<String, Object> from(BindingResult bindingResult) {
    Map<String, Object> errors = new LinkedHashMap<>();
    bindingResult
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return errors;
  }
}
