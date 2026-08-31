package com.bookfair.backend.event.organization;

import com.bookfair.backend.model.enums.OrganizationRole;
import java.util.UUID;

public record OrganizationInviteSentEvent(
    UUID orgId,
    String orgName,
    String inviteeEmail,
    OrganizationRole assignedRole,
    String acceptLink) {}
