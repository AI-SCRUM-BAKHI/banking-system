package com.scrumai.identity.mapper;

import com.scrumai.identity.dto.UserResponse;
import com.scrumai.identity.entity.UserEntity;

public final class UserMapper {
  private UserMapper() {}

  public static UserResponse toResponse(UserEntity user) {
    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getFullName(),
        user.getEmail(),
        user.getRole(),
        user.getStatus(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
