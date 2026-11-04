package com.scrumai.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {
  @Test
  void swaggerSendsTheAuthenticationRequiredByEachEndpoint() {
    OpenApiConfig config = new OpenApiConfig();
    var api = config.scrumAiOpenApi();
    api.setPaths(
        new Paths()
            .addPathItem("/api/v1/auth/login", new PathItem().post(new Operation()))
            .addPathItem("/api/v1/auth/refresh", new PathItem().post(new Operation()))
            .addPathItem("/api/v1/system/ping", new PathItem().get(new Operation()))
            .addPathItem("/api/v1/auth/me", new PathItem().get(new Operation()))
            .addPathItem("/api/v1/transactions", new PathItem().post(new Operation()))
            .addPathItem(
                "/api/v1/fraud-transactions",
                new PathItem().get(new Operation()).post(new Operation()))
            .addPathItem("/api/v1/transactions/evaluate", new PathItem().post(new Operation())));

    config.operationAuthentication().customise(api);

    var schemes = api.getComponents().getSecuritySchemes();
    assertThat(schemes.get("bearerAuth").getType()).isEqualTo(SecurityScheme.Type.HTTP);
    assertThat(schemes.get("bearerAuth").getScheme()).isEqualTo("bearer");
    assertThat(schemes.get("serviceToken").getType()).isEqualTo(SecurityScheme.Type.APIKEY);
    assertThat(schemes.get("serviceToken").getName()).isEqualTo("X-Service-Token");
    assertThat(schemes.get("serviceToken").getIn()).isEqualTo(SecurityScheme.In.HEADER);
    assertThat(api.getSecurity())
        .singleElement()
        .satisfies(r -> assertThat(r).containsKey("bearerAuth"));
    assertThat(api.getPaths().get("/api/v1/auth/login").getPost().getSecurity()).isEmpty();
    assertThat(api.getPaths().get("/api/v1/auth/refresh").getPost().getSecurity()).isEmpty();
    assertThat(api.getPaths().get("/api/v1/system/ping").getGet().getSecurity()).isEmpty();
    assertThat(api.getPaths().get("/api/v1/auth/me").getGet().getSecurity()).isNull();
    assertThat(api.getPaths().get("/api/v1/fraud-transactions").getGet().getSecurity()).isNull();
    for (String path :
        new String[] {
          "/api/v1/fraud-transactions", "/api/v1/transactions/evaluate", "/api/v1/transactions"
        }) {
      assertThat(api.getPaths().get(path).getPost().getSecurity())
          .singleElement()
          .satisfies(r -> assertThat(r).containsOnlyKeys("serviceToken"));
    }
  }
}
