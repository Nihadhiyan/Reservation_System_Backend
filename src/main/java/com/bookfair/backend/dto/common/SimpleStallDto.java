package com.bookfair.backend.dto.common;

import java.util.UUID;
public record SimpleStallDto(
    UUID id,
    String name,
    String stallType,
    Double squareFootage
) {}
