package com.scrumai.auth.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scrumai.identity.entity.UserEntity;
import com.scrumai.identity.enums.UserRole;
import com.scrumai.identity.repository.UserRepository;
import jakarta.servlet.Filter;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

class AiAnalysisSecurityTest {
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
        context,
        "app.auth.jwt-secret=" + Base64.getEncoder().encodeToString(new byte[32]),
        "app.auth.jwt-issuer=scrum-ai",
        "app.auth.service-token=service-test");
    context.register(Config.class);
    context.refresh();
    mvc =
        MockMvcBuilders.standaloneSetup(new Endpoint())
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
            .build();
  }

  @AfterEach
  void close() {
    context.close();
  }

  @ParameterizedTest
  @CsvSource({"GET,ADMIN", "POST,ADMIN", "GET,BANK_USER", "POST,BANK_USER"})
  void permitsAuthenticatedBankUsers(String method, UserRole role) throws Exception {
    UUID id = UUID.randomUUID();
    UserEntity user = UserEntity.create("user", "hash", "User", "user@test.local", role);
    user.setId(id);
    when(context.getBean(UserRepository.class).findById(id)).thenReturn(Optional.of(user));
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("scrum-ai")
            .subject(id.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("type", "access")
            .build();
    String token =
        context
            .getBean(JwtEncoder.class)
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    mvc.perform(
            request(HttpMethod.valueOf(method), "/api/v1/transactions/TX-1/ai-analysis")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "POST"})
  void serviceTokenDoesNotGrantAnalysisAccess(String method) throws Exception {
    mvc.perform(
            request(HttpMethod.valueOf(method), "/api/v1/transactions/TX-1/ai-analysis")
                .header("X-Service-Token", "service-test"))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "POST"})
  void rejectsAnonymous(String method) throws Exception {
    mvc.perform(request(HttpMethod.valueOf(method), "/api/v1/transactions/TX-1/ai-analysis"))
        .andExpect(status().isUnauthorized());
  }

  @Configuration
  @EnableWebSecurity
  @Import(SecurityConfig.class)
  static class Config {
    @Bean
    UserRepository users() {
      return mock(UserRepository.class);
    }

    @Bean
    ObjectMapper mapper() {
      return new ObjectMapper();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
      return request -> {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("http://localhost:5173"));
        return cors;
      };
    }
  }

  @RestController
  static class Endpoint {
    @RequestMapping("/api/v1/transactions/{ref}/ai-analysis")
    String analysis() {
      return "ok";
    }
  }
}
