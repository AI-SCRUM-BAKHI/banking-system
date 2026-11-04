package com.scrumai.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scrumai.auth.dto.CurrentUserResponse;
import com.scrumai.auth.dto.LoginRequest;
import com.scrumai.auth.dto.TokenResponse;
import com.scrumai.auth.entity.RefreshTokenEntity;
import com.scrumai.auth.repository.RefreshTokenRepository;
import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.identity.entity.UserEntity;
import com.scrumai.identity.enums.UserRole;
import com.scrumai.identity.enums.UserStatus;
import com.scrumai.identity.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository users;
  @Mock private RefreshTokenRepository refreshTokens;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtEncoder jwtEncoder;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(users, refreshTokens, passwordEncoder, jwtEncoder, "scrum-ai-test");
  }

  private UserEntity createUser(UUID id, String username, UserStatus status) {
    UserEntity user =
        UserEntity.create(
            username, "$2a$10$hashedPassword", "Test User", "test@example.com", UserRole.BANK_USER);
    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "status", status);
    return user;
  }

  private void mockJwtEncoding() {
    Jwt jwt =
        Jwt.withTokenValue("mock-access-token")
            .header("alg", "HS256")
            .claim("sub", "user-id")
            .claim("type", "access")
            .build();
    when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);
  }

  @Nested
  @DisplayName("Login Tests")
  class LoginTests {

    @Test
    @DisplayName("Should successfully login when credentials are valid and user is active")
    void login_success() {
      UUID userId = UUID.randomUUID();
      UserEntity user = createUser(userId, "john_doe", UserStatus.ACTIVE);
      when(users.findByUsername("john_doe")).thenReturn(Optional.of(user));
      when(passwordEncoder.matches("password123", "$2a$10$hashedPassword")).thenReturn(true);
      mockJwtEncoding();

      TokenResponse response = authService.login(new LoginRequest("john_doe", "password123"));

      assertThat(response).isNotNull();
      assertThat(response.accessToken()).isEqualTo("mock-access-token");
      assertThat(response.tokenType()).isEqualTo("Bearer");
      assertThat(response.refreshToken()).isNotBlank();
      assertThat(response.expiresIn()).isEqualTo(900L);

      ArgumentCaptor<RefreshTokenEntity> tokenCaptor =
          ArgumentCaptor.forClass(RefreshTokenEntity.class);
      verify(refreshTokens).save(tokenCaptor.capture());
      RefreshTokenEntity saved = tokenCaptor.getValue();
      assertThat(saved.getUser()).isEqualTo(user);
      assertThat(saved.getTokenHash()).isNotBlank();
      assertThat(saved.getExpiresAt()).isAfter(Instant.now());
      assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when username is not found")
    void login_usernameNotFound() {
      when(users.findByUsername("unknown")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", "password")))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when password does not match")
    void login_wrongPassword() {
      UserEntity user = createUser(UUID.randomUUID(), "john_doe", UserStatus.ACTIVE);
      when(users.findByUsername("john_doe")).thenReturn(Optional.of(user));
      when(passwordEncoder.matches("wrongpass", "$2a$10$hashedPassword")).thenReturn(false);

      assertThatThrownBy(() -> authService.login(new LoginRequest("john_doe", "wrongpass")))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when user is disabled")
    void login_userDisabled() {
      UserEntity user = createUser(UUID.randomUUID(), "john_doe", UserStatus.DISABLED);
      when(users.findByUsername("john_doe")).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> authService.login(new LoginRequest("john_doe", "password123")))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
  }

  @Nested
  @DisplayName("Refresh Tests")
  class RefreshTests {

    @Test
    @DisplayName("Should rotate tokens when refresh token is valid")
    void refresh_success() {
      UserEntity user = createUser(UUID.randomUUID(), "john_doe", UserStatus.ACTIVE);
      RefreshTokenEntity existing = new RefreshTokenEntity();
      existing.setUser(user);
      existing.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));
      existing.setRevokedAt(null);

      when(refreshTokens.findForUpdate(anyString())).thenReturn(Optional.of(existing));
      mockJwtEncoding();

      TokenResponse response = authService.refresh("some-refresh-token");

      assertThat(response).isNotNull();
      assertThat(response.accessToken()).isEqualTo("mock-access-token");
      assertThat(existing.getRevokedAt()).isNotNull();
      verify(refreshTokens).save(any(RefreshTokenEntity.class));
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when refresh token does not exist")
    void refresh_tokenNotFound() {
      when(refreshTokens.findForUpdate(anyString())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> authService.refresh("non-existent-token"))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when refresh token is already revoked")
    void refresh_alreadyRevoked() {
      RefreshTokenEntity existing = new RefreshTokenEntity();
      existing.setRevokedAt(Instant.now().minus(1, ChronoUnit.HOURS));

      when(refreshTokens.findForUpdate(anyString())).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> authService.refresh("revoked-token"))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when refresh token has expired")
    void refresh_expiredToken() {
      RefreshTokenEntity existing = new RefreshTokenEntity();
      existing.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
      existing.setRevokedAt(null);

      when(refreshTokens.findForUpdate(anyString())).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> authService.refresh("expired-token"))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when user is disabled")
    void refresh_userDisabled() {
      UserEntity user = createUser(UUID.randomUUID(), "john_doe", UserStatus.DISABLED);
      RefreshTokenEntity existing = new RefreshTokenEntity();
      existing.setUser(user);
      existing.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));
      existing.setRevokedAt(null);

      when(refreshTokens.findForUpdate(anyString())).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> authService.refresh("valid-token"))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
  }

  @Nested
  @DisplayName("Logout Tests")
  class LogoutTests {

    @Test
    @DisplayName("Should revoke token when user id matches")
    void logout_matchesUser() {
      UUID userId = UUID.randomUUID();
      UserEntity user = createUser(userId, "john_doe", UserStatus.ACTIVE);
      RefreshTokenEntity token = new RefreshTokenEntity();
      token.setUser(user);
      token.setRevokedAt(null);

      when(refreshTokens.findForUpdate(anyString())).thenReturn(Optional.of(token));

      authService.logout("my-refresh-token", userId);

      assertThat(token.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should revoke token when user id is null (unauthenticated / expired access token)")
    void logout_nullUserIdAllowed() {
      UUID userId = UUID.randomUUID();
      UserEntity user = createUser(userId, "john_doe", UserStatus.ACTIVE);
      RefreshTokenEntity token = new RefreshTokenEntity();
      token.setUser(user);
      token.setRevokedAt(null);

      when(refreshTokens.findForUpdate(anyString())).thenReturn(Optional.of(token));

      authService.logout("my-refresh-token", null);

      assertThat(token.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should not revoke token if user id belongs to another user")
    void logout_differentUserDoesNotRevoke() {
      UUID ownerId = UUID.randomUUID();
      UUID callerId = UUID.randomUUID();
      UserEntity user = createUser(ownerId, "owner", UserStatus.ACTIVE);
      RefreshTokenEntity token = new RefreshTokenEntity();
      token.setUser(user);
      token.setRevokedAt(null);

      when(refreshTokens.findForUpdate(anyString())).thenReturn(Optional.of(token));

      authService.logout("my-refresh-token", callerId);

      assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("Should do nothing gracefully if token is not found")
    void logout_notFoundGraceful() {
      when(refreshTokens.findForUpdate(anyString())).thenReturn(Optional.empty());

      authService.logout("non-existent-token", UUID.randomUUID());
      // No exception expected
    }
  }

  @Nested
  @DisplayName("Current User (me) Tests")
  class MeTests {

    @Test
    @DisplayName("Should return current user response when user is active")
    void me_success() {
      UUID userId = UUID.randomUUID();
      UserEntity user = createUser(userId, "john_doe", UserStatus.ACTIVE);
      when(users.findById(userId)).thenReturn(Optional.of(user));

      CurrentUserResponse response = authService.me(userId);

      assertThat(response).isNotNull();
      assertThat(response.id()).isEqualTo(userId);
      assertThat(response.username()).isEqualTo("john_doe");
      assertThat(response.role()).isEqualTo(UserRole.BANK_USER);
      assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when user is not found")
    void me_notFound() {
      UUID userId = UUID.randomUUID();
      when(users.findById(userId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> authService.me(userId))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when user is disabled")
    void me_userDisabled() {
      UUID userId = UUID.randomUUID();
      UserEntity user = createUser(userId, "john_doe", UserStatus.DISABLED);
      when(users.findById(userId)).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> authService.me(userId))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
  }
}
