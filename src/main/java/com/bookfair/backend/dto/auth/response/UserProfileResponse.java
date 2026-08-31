package com.bookfair.backend.dto.auth.response;

import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String username,
    String email,
    String role,
    String businessName,
    String contactNumber,
    String address,
    Boolean active
) {}
