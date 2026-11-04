package com.scrumai.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scrumai.auth.dto.CurrentUserResponse;
import com.scrumai.auth.dto.LoginRequest;
import com.scrumai.auth.dto.TokenResponse;
import com.scrumai.auth.service.AuthService;
import com.scrumai.common.exception.GlobalExceptionHandler;
import com.scrumai.identity.enums.UserRole;
import com.scrumai.identity.enums.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  private MockMvc mockMvc;

  @Mock private AuthService authService;

  @InjectMocks private AuthController authController;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("POST /api/v1/auth/login - should return token response on valid body")
  void login_success() throws Exception {
    TokenResponse tokenResponse =
        new TokenResponse("mock-jwt-token", "mock-refresh-token", "Bearer", 900L);
    when(authService.login(any(LoginRequest.class))).thenReturn(tokenResponse);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "admin",
                      "password": "Password123!"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("mock-jwt-token"))
        .andExpect(jsonPath("$.data.refreshToken").value("mock-refresh-token"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(900));
  }

  @Test
  @DisplayName("POST /api/v1/auth/login - should return 400 when missing fields")
  void login_validationFailure() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "",
                      "password": ""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/refresh - should return refreshed tokens")
  void refresh_success() throws Exception {
    TokenResponse tokenResponse =
        new TokenResponse("new-jwt-token", "new-refresh-token", "Bearer", 900L);
    when(authService.refresh("old-refresh-token")).thenReturn(tokenResponse);

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "old-refresh-token"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("new-jwt-token"))
        .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/logout - should return loggedOut=true")
  void logout_success() throws Exception {
    UUID userId = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(userId.toString(), null);

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "sample-refresh-token"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.loggedOut").value(true));

    verify(authService).logout(eq("sample-refresh-token"), eq(userId));
  }

  @Test
  @DisplayName("POST /api/v1/auth/logout - should allow unauthenticated logout when access token expired")
  void logout_withoutPrincipal() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "sample-refresh-token"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.loggedOut").value(true));

    verify(authService).logout(eq("sample-refresh-token"), eq(null));
  }

  @Test
  @DisplayName("GET /api/v1/auth/me - should return current user profile")
  void me_success() throws Exception {
    UUID userId = UUID.randomUUID();
    CurrentUserResponse profile =
        new CurrentUserResponse(
            userId, "admin", "Admin User", "admin@bankshield.com", UserRole.ADMIN, UserStatus.ACTIVE);
    when(authService.me(userId)).thenReturn(profile);

    Authentication auth = new UsernamePasswordAuthenticationToken(userId.toString(), null);

    mockMvc
        .perform(get("/api/v1/auth/me").principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.username").value("admin"))
        .andExpect(jsonPath("$.data.role").value("ADMIN"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }
}
