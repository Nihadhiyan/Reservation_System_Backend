package com.bookfair.backend.dto.event.response;

import java.time.Instant;
import java.util.UUID;

public record EventSummaryResponse(
    UUID id,
    String name,
    String eventType,
    Instant startDate,
    Instant endDate,
    String status,
    Boolean active
) {}
