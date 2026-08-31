package com.bookfair.backend.dto.organization.response;

import java.util.Set;
import java.util.UUID;
public record OrganizationResponse(
    UUID id,
    String name,
    String contactNumber,
    String contactEmail,
    String billingAddress,
    Set<String> capabilities,
    Boolean active
) {}