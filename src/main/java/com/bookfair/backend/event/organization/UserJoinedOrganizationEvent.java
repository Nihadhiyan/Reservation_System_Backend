package com.bookfair.backend.event.organization;

import com.bookfair.backend.model.enums.OrganizationRole;
import java.util.UUID;

public record UserJoinedOrganizationEvent(
    UUID orgId,
    String orgName,
    UUID userId,
    String username,
    String userEmail,
    OrganizationRole assignedRole) {}
