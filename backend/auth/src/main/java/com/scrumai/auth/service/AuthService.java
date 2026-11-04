package com.scrumai.auth.service;

import com.scrumai.auth.dto.CurrentUserResponse;
import com.scrumai.auth.dto.LoginRequest;
import com.scrumai.auth.dto.TokenResponse;
import com.scrumai.auth.entity.RefreshTokenEntity;
import com.scrumai.auth.repository.RefreshTokenRepository;
import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.identity.entity.UserEntity;
import com.scrumai.identity.enums.UserStatus;
import com.scrumai.identity.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
  private static final Duration REFRESH_LIFETIME = Duration.ofDays(7);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final String issuer;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      JwtEncoder jwtEncoder,
      @Value("${app.auth.jwt-issuer:scrum-ai}") String issuer) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtEncoder = jwtEncoder;
    this.issuer = issuer;
  }

  @Transactional
  public TokenResponse login(LoginRequest request) {
    UserEntity user =
        users.findByUsername(request.username()).orElseThrow(AuthService::invalidCredentials);
    if (user.getStatus() != UserStatus.ACTIVE
        || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw invalidCredentials();
    }
    return issueTokens(user);
  }

  @Transactional
  public TokenResponse refresh(String suppliedToken) {
    RefreshTokenEntity old =
        refreshTokens
            .findForUpdate(hash(suppliedToken))
            .orElseThrow(AuthService::invalidRefreshToken);
    Instant now = Instant.now();
    if (old.getRevokedAt() != null
        || !old.getExpiresAt().isAfter(now)
        || old.getUser().getStatus() != UserStatus.ACTIVE) {
      throw invalidRefreshToken();
    }
    old.setRevokedAt(now);
    return issueTokens(old.getUser());
  }

  @Transactional
  public void logout(String suppliedToken, UUID userId) {
    refreshTokens
        .findForUpdate(hash(suppliedToken))
        .ifPresent(
            token -> {
              if ((userId == null || token.getUser().getId().equals(userId))
                  && token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
              }
            });
  }

  @Transactional(readOnly = true)
  public CurrentUserResponse me(UUID userId) {
    UserEntity user =
        users
            .findById(userId)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid access token"));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid access token");
    }
    return CurrentUserResponse.from(user);
  }

  private TokenResponse issueTokens(UserEntity user) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plus(ACCESS_LIFETIME))
            .claim("type", "access")
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String accessToken =
        jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    byte[] random = new byte[48];
    RANDOM.nextBytes(random);
    String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    RefreshTokenEntity entity = new RefreshTokenEntity();
    entity.setUser(user);
    entity.setTokenHash(hash(refreshToken));
    entity.setExpiresAt(now.plus(REFRESH_LIFETIME));
    refreshTokens.save(entity);
    return new TokenResponse(accessToken, refreshToken, "Bearer", ACCESS_LIFETIME.toSeconds());
  }

  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private static BusinessException invalidCredentials() {
    return new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid username or password");
  }

  private static BusinessException invalidRefreshToken() {
    return new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token", Map.of());
  }
}
