package com.scrumai.auth.dto;

public record TokenResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn) {}
