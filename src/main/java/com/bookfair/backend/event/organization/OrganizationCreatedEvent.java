package com.bookfair.backend.event.organization;

import java.util.UUID;

public record OrganizationCreatedEvent(UUID organizationId) implements OrganizationEvent {
}
