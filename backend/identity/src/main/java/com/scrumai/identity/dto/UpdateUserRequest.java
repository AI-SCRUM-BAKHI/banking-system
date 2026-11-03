package com.scrumai.identity.dto;

import com.scrumai.identity.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @Size(min = 1, max = 120) String fullName,
    @Email @Size(min = 1, max = 254) String email,
    UserRole role) {}
