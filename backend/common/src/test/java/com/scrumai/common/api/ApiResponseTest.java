package com.scrumai.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {
  @Test
  void successResponseContainsDataAndMessage() {
    ApiResponse<String> response = ApiResponse.success("ok");

    assertThat(response.success()).isTrue();
    assertThat(response.data()).isEqualTo("ok");
    assertThat(response.message()).isEqualTo("Request completed successfully");
    assertThat(response.timestamp()).isNotNull();
  }
}
