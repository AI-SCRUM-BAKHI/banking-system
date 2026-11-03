package com.scrumai.identity.dto;

import com.scrumai.identity.enums.UserRole;
import com.scrumai.identity.enums.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    String fullName,
    String email,
    UserRole role,
    UserStatus status,
    Instant createdAt,
    Instant updatedAt) {}
