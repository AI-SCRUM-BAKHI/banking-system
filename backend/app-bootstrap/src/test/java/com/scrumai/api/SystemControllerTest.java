package com.scrumai.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.scrumai.common.api.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SystemControllerTest {
  @Test
  void pingReturnsHealthyResponse() {
    ApiResponse<Map<String, String>> response = new SystemController().ping();

    assertThat(response.success()).isTrue();
    assertThat(response.data()).containsEntry("status", "UP");
    assertThat(response.requestId()).isEqualTo("unknown");
  }
}
