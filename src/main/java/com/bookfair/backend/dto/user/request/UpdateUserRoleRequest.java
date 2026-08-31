package com.bookfair.backend.dto.user.request;

import com.bookfair.backend.model.enums.SystemRole;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
    @NotNull(message = "Role is required")
    SystemRole role
) {}
