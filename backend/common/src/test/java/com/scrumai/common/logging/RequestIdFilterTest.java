package com.scrumai.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.scrumai.common.api.RequestId;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
  private final RequestIdFilter filter = new RequestIdFilter();

  @AfterEach
  void clearRequestId() {
    RequestId.clear();
  }

  @Test
  void preservesClientRequestId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestId.HEADER, "request-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (servletRequest, servletResponse) -> {};

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(RequestId.HEADER)).isEqualTo("request-123");
  }
}
