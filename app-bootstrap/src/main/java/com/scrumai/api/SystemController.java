package com.scrumai.api;

import com.scrumai.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
  @GetMapping("/ping")
  public ApiResponse<Map<String, String>> ping() {
    return ApiResponse.success(Map.of("status", "UP"));
  }
}
