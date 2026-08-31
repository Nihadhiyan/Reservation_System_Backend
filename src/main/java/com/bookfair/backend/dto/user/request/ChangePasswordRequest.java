package com.bookfair.backend.dto.user.request;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank(message = "Current Password is required")
    String oldPassword,

    @NotBlank(message = "New Password is required")
    String newPassword
) {}
