package com.scrumai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(max = 50) String username,
    @NotBlank @Size(min = 12, max = 72) String password,
    @NotBlank @Size(max = 120) String fullName,
    @NotBlank @Email @Size(max = 254) String email) {}