package com.bookfair.backend.dto.user.response;

import java.util.UUID;

import com.bookfair.backend.dto.common.SimpleOrganizationDto;

public record UserResponse(
    UUID id,
    String username,
    String email,
    String role,
    String contactNumber,
    String address,
    SimpleOrganizationDto organization
) {}
