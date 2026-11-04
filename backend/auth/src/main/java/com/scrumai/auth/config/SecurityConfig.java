package com.scrumai.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.scrumai.common.api.ApiErrorResponse;
import com.scrumai.identity.entity.UserEntity;
import com.scrumai.identity.enums.UserStatus;
import com.scrumai.identity.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecretKey jwtKey(@Value("${app.auth.jwt-secret:}") String encodedSecret) {
    if (encodedSecret.isBlank()) {
      throw new IllegalStateException("APP_JWT_SECRET must be set to a base64-encoded 32-byte key");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(encodedSecret);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("APP_JWT_SECRET must be base64 encoded", exception);
    }
    if (bytes.length < 32) {
      throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 random bytes");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtKey) {
    OctetSequenceKey key =
        new OctetSequenceKey.Builder(jwtKey)
            .algorithm(com.nimbusds.jose.JWSAlgorithm.HS256)
            .build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey jwtKey, @Value("${app.auth.jwt-issuer:scrum-ai}") String issuer) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter(UserRepository users) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName("sub");
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          if (!"access".equals(jwt.getClaimAsString("type"))) {
            return List.of();
          }
          try {
            UserEntity user = users.findById(UUID.fromString(jwt.getSubject())).orElse(null);
            if (user == null || user.getStatus() != UserStatus.ACTIVE) {
              return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
          } catch (IllegalArgumentException exception) {
            return List.of();
          }
        });
    return converter;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      UserRepository users,
      ObjectMapper mapper,
      @Qualifier("corsConfigurationSource")
      CorsConfigurationSource corsConfigurationSource,
      @Value("${app.auth.service-token:}") String serviceToken)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/system/ping",
                        "/actuator/health/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/v1/fraud-transactions")
                    .hasRole("SERVICE")
                    .requestMatchers("/api/v1/users/me/password")
                    .hasAnyRole("ADMIN", "BANK_USER")
                    .requestMatchers("/api/v1/users/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        "/api/v1/fraud-transactions/**", "/api/v1/auth/me", "/api/v1/auth/logout")
                    .hasAnyRole("ADMIN", "BANK_USER")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(users)))
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeError(
                                mapper,
                                response,
                                request.getRequestURI(),
                                401,
                                "UNAUTHORIZED",
                                "Authentication required")))
        .exceptionHandling(
            errors ->
                errors.accessDeniedHandler(
                    (request, response, exception) ->
                        writeError(
                            mapper,
                            response,
                            request.getRequestURI(),
                            403,
                            "ACCESS_DENIED",
                            "Access denied")));
    http.addFilterBefore(
        new ServiceTokenFilter(serviceToken), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private static void writeError(
      ObjectMapper mapper,
      HttpServletResponse response,
      String path,
      int status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(
        response.getOutputStream(), ApiErrorResponse.of(code, message, status, path, Map.of()));
  }
}
