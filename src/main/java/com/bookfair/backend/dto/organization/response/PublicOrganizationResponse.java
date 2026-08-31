package com.bookfair.backend.dto.organization.response;

import java.util.Set;
import java.util.UUID;

public record PublicOrganizationResponse(
    UUID id,
    String name,
    Set<String> capabilities
) {}
