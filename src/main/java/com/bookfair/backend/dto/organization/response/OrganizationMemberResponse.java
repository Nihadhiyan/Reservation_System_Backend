package com.bookfair.backend.dto.organization.response;

import java.time.Instant;
import java.util.UUID;

public record OrganizationMemberResponse(
    UUID id,
    UUID userId,
    String username,
    String email,
    String role,
    Boolean active,
    Instant joinedAt
) {}
