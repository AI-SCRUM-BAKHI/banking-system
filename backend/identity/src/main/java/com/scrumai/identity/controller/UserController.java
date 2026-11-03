package com.scrumai.identity.controller;

import com.scrumai.common.api.ApiResponse;
import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.identity.dto.ChangePasswordRequest;
import com.scrumai.identity.dto.CreateUserRequest;
import com.scrumai.identity.dto.ResetPasswordRequest;
import com.scrumai.identity.dto.UpdateUserRequest;
import com.scrumai.identity.dto.UpdateUserStatusRequest;
import com.scrumai.identity.dto.UserPageResponse;
import com.scrumai.identity.dto.UserResponse;
import com.scrumai.identity.enums.UserRole;
import com.scrumai.identity.enums.UserStatus;
import com.scrumai.identity.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<UserResponse>> create(
      @Valid @RequestBody CreateUserRequest request) {
    UserResponse user = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/users/" + user.id()))
        .body(ApiResponse.success(user, "User created"));
  }

  @GetMapping
  public ApiResponse<UserPageResponse> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) UserRole role,
      @RequestParam(required = false) UserStatus status) {
    return ApiResponse.success(service.list(page, size, role, status));
  }

  @GetMapping("/{id}")
  public ApiResponse<UserResponse> get(@PathVariable UUID id) {
    return ApiResponse.success(service.get(id));
  }

  @PatchMapping("/{id}")
  public ApiResponse<UserResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
    return ApiResponse.success(service.update(id, request), "User updated");
  }

  @PatchMapping("/{id}/status")
  public ApiResponse<UserResponse> updateStatus(
      @PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request) {
    return ApiResponse.success(service.updateStatus(id, request.status()), "User status updated");
  }

  @PatchMapping("/me/password")
  public ApiResponse<Void> changeOwnPassword(
      Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
    service.changeOwnPassword(currentUserId(authentication), request);
    return ApiResponse.success(null, "Password changed");
  }

  @PatchMapping("/{id}/password")
  public ApiResponse<Void> resetPassword(
      @PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
    service.resetPassword(id, request);
    return ApiResponse.success(null, "Password reset");
  }

  private static UUID currentUserId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
    }
    try {
      return UUID.fromString(authentication.getName());
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid authentication identity");
    }
  }
}
