package com.scrumai.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scrumai.auth.dto.LoginRequest;
import com.scrumai.auth.dto.TokenResponse;
import com.scrumai.auth.entity.RefreshTokenEntity;
import com.scrumai.auth.repository.RefreshTokenRepository;
import com.scrumai.auth.service.AuthService;
import com.scrumai.identity.entity.UserEntity;
import com.scrumai.identity.enums.UserRole;
import com.scrumai.identity.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.KeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class AuthTokenIntegrationTest {
  private AuthService service;
  private RefreshTokenRepository refreshTokens;
  private JwtDecoder decoder;
  private UserEntity user;

  @BeforeEach
  void setup() throws Exception {
    SecurityConfig config = new SecurityConfig();
    KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256");
    generator.init(256);
    var key =
        config.jwtKey(Base64.getEncoder().encodeToString(generator.generateKey().getEncoded()));
    decoder = config.jwtDecoder(key, "auth-test");
    UserRepository users = mock(UserRepository.class);
    refreshTokens = mock(RefreshTokenRepository.class);
    PasswordEncoder passwords = config.passwordEncoder();
    user =
        UserEntity.create(
            "test-admin",
            passwords.encode("test-password"),
            "Test Admin",
            "test-admin@example.test",
            UserRole.ADMIN);
    user.setId(UUID.randomUUID());
    when(users.findByUsername("test-admin")).thenReturn(Optional.of(user));
    service = new AuthService(users, refreshTokens, passwords, config.jwtEncoder(key), "auth-test");
  }

  @Test
  void loginIssuesATokenAcceptedByTheConfiguredDecoder() {
    TokenResponse tokens = service.login(new LoginRequest("test-admin", "test-password"));

    assertAccessToken(tokens);
    ArgumentCaptor<RefreshTokenEntity> saved = ArgumentCaptor.forClass(RefreshTokenEntity.class);
    verify(refreshTokens).save(saved.capture());
    assertThat(saved.getValue().getUser()).isSameAs(user);
    assertThat(saved.getValue().getTokenHash())
        .matches("[0-9a-f]{64}")
        .isNotEqualTo(tokens.refreshToken());
  }

  @Test
  void refreshRotatesTheTokenAndIssuesAnotherValidAccessToken() throws Exception {
    String supplied = "existing-test-refresh-token";
    RefreshTokenEntity old = new RefreshTokenEntity();
    old.setUser(user);
    old.setExpiresAt(Instant.now().plusSeconds(3600));
    when(refreshTokens.findForUpdate(anyString())).thenReturn(Optional.of(old));

    TokenResponse tokens = service.refresh(supplied);

    assertAccessToken(tokens);
    assertThat(old.getRevokedAt()).isNotNull();
    assertThat(tokens.refreshToken()).isNotEqualTo(supplied);
    verify(refreshTokens)
        .findForUpdate(
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(supplied.getBytes(StandardCharsets.UTF_8))));
  }

  private void assertAccessToken(TokenResponse tokens) {
    Jwt jwt = decoder.decode(tokens.accessToken());
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
    assertThat(jwt.getClaimAsString("type")).isEqualTo("access");
    assertThat(tokens.tokenType()).isEqualTo("Bearer");
    assertThat(tokens.expiresIn()).isEqualTo(900);
  }
}
