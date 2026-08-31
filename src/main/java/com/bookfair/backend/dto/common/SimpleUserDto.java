package com.bookfair.backend.dto.common;

import java.util.UUID;
public record SimpleUserDto(
    UUID id,
    String username,
    String email
) {}
