package com.bookfair.backend.dto.organization.request;

import com.bookfair.backend.model.enums.OrganizationRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InviteRequest(
    @NotNull(message = "Organization ID is required")
    UUID orgId,

    @NotNull(message = "Email is required")
    @Email(message = "Valid email is required")
    String email,

    @NotNull(message = "Role is required")
    OrganizationRole role,

    // Set true to proceed after already having been warned (via
    // ErrorCode.CONFIRMATION_REQUIRED) that the invitee is an ORG_ADMIN in
    // another organization. Absent/false on the inviter's first attempt.
    Boolean confirmed
) {}
