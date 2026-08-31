package com.bookfair.backend.dto.auth.response;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    Long expiresIn,
    UserProfileResponse user
) {}
