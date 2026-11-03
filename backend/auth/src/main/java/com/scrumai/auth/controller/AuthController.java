package com.scrumai.auth.controller;

import com.scrumai.auth.dto.CurrentUserResponse;
import com.scrumai.auth.dto.LoginRequest;
import com.scrumai.auth.dto.RefreshRequest;
import com.scrumai.auth.dto.TokenResponse;
import com.scrumai.auth.service.AuthService;
import com.scrumai.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.success(auth.login(request));
  }

  @PostMapping("/refresh")
  public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ApiResponse.success(auth.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  public ApiResponse<Map<String, Boolean>> logout(
      @Valid @RequestBody RefreshRequest request, Authentication authentication) {
    auth.logout(request.refreshToken(), UUID.fromString(authentication.getName()));
    return ApiResponse.success(Map.of("loggedOut", true));
  }

  @GetMapping("/me")
  public ApiResponse<CurrentUserResponse> me(Authentication authentication) {
    return ApiResponse.success(auth.me(UUID.fromString(authentication.getName())));
  }
}
