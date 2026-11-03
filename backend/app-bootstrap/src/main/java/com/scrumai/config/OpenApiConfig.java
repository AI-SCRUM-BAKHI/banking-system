package com.scrumai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI scrumAiOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Scrum-AI Backend API")
                .description("Foundation API for the Scrum-AI project")
                .version("0.0.1"));
  }
}
