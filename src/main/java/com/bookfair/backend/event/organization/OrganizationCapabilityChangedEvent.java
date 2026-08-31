package com.bookfair.backend.event.organization;

import java.util.Set;
import java.util.UUID;

import com.bookfair.backend.model.enums.OrganizationCapability;
public record OrganizationCapabilityChangedEvent(
        UUID organizationId,
        Set<OrganizationCapability> newCapability) implements OrganizationEvent {
}
