package com.scrumai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
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
                .version("0.0.1"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste data.accessToken from POST /api/v1/auth/login."))
                .addSecuritySchemes(
                    "serviceToken",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Service-Token")
                        .description(
                            "Use APP_SERVICE_TOKEN from backend/.env for transaction evaluation and fraud ingest.")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }

  @Bean
  OpenApiCustomizer operationAuthentication() {
    Set<String> publicPaths =
        Set.of("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/system/ping");
    Set<String> servicePaths =
        Set.of("/api/v1/fraud-transactions", "/api/v1/transactions/evaluate");
    return api -> {
      if (api.getPaths() == null) {
        return;
      }
      api.getPaths()
          .forEach(
              (path, item) -> {
                if (publicPaths.contains(path)) {
                  item.readOperations().forEach(operation -> operation.setSecurity(List.of()));
                }
                if (servicePaths.contains(path) && item.getPost() != null) {
                  item.getPost()
                      .setSecurity(List.of(new SecurityRequirement().addList("serviceToken")));
                }
              });
    };
  }
}
