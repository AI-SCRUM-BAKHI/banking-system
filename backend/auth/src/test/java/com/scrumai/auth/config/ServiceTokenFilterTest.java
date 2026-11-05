package com.scrumai.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class ServiceTokenFilterTest {
  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/transactions/evaluate",
        "/api/v1/fraud-transactions",
        "/api/v1/transactions"
      })
  void acceptsTrustedServiceToken(String path) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setServletPath(path);
    request.addHeader("X-Service-Token", "test-secret");
    new ServiceTokenFilter("test-secret")
        .doFilter(
            request,
            new MockHttpServletResponse(),
            (req, res) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting(Object::toString)
                    .containsExactly("ROLE_SERVICE"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "wrong"})
  void rejectsMissingOrInvalidToken(String token) throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/v1/transactions/evaluate");
    request.setServletPath("/api/v1/transactions/evaluate");
    request.addHeader("X-Service-Token", token);
    new ServiceTokenFilter("test-secret")
        .doFilter(
            request,
            new MockHttpServletResponse(),
            (req, res) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
  }
}
