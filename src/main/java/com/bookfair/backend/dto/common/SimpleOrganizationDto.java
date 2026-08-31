package com.bookfair.backend.dto.common;

import java.util.Set;
import java.util.UUID;

import com.bookfair.backend.model.enums.OrganizationCapability;

public record SimpleOrganizationDto(
    UUID id,
    String name,
    Set<OrganizationCapability> capabilities
) {}