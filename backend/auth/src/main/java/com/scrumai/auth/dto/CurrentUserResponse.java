package com.scrumai.auth.dto;

import com.scrumai.identity.entity.UserEntity;
import com.scrumai.identity.enums.UserRole;
import com.scrumai.identity.enums.UserStatus;
import java.util.UUID;

public record CurrentUserResponse(
    UUID id, String username, String fullName, String email, UserRole role, UserStatus status) {
  public static CurrentUserResponse from(UserEntity user) {
    return new CurrentUserResponse(
        user.getId(),
        user.getUsername(),
        user.getFullName(),
        user.getEmail(),
        user.getRole(),
        user.getStatus());
  }
}
