package com.bookfair.backend.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
public record VerifyEmailRequest(
    @NotBlank(message = "Verification token is required")
    String token
) {}