package com.bookfair.backend.dto.common;

import java.time.Instant;
import java.util.UUID;
public record SimpleEventDto(
    UUID id,
    String name,
    Instant startDate,
    Instant endDate
) {}
