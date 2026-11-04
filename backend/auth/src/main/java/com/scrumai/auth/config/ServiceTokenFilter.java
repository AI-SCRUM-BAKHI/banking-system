package com.scrumai.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class ServiceTokenFilter extends OncePerRequestFilter {
  private final String expectedToken;

  ServiceTokenFilter(String expectedToken) {
    this.expectedToken = expectedToken;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (HttpMethod.POST.matches(request.getMethod())
        && ("/api/v1/fraud-transactions".equals(request.getServletPath())
            || "/api/v1/transactions/evaluate".equals(request.getServletPath()))
        && !expectedToken.isBlank()) {
      String supplied = request.getHeader("X-Service-Token");
      if (supplied != null
          && MessageDigest.isEqual(
              supplied.getBytes(StandardCharsets.UTF_8),
              expectedToken.getBytes(StandardCharsets.UTF_8))) {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "fraud-ingest", null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
      }
    }
    chain.doFilter(request, response);
  }
}
