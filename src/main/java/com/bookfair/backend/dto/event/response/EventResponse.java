package com.bookfair.backend.dto.event.response;

import java.time.Instant;
import java.util.UUID;

import com.bookfair.backend.dto.common.SimpleOrganizationDto;

public record EventResponse(
    UUID id,
    String name,
    String eventType,
    SimpleOrganizationDto organizer,
    UUID venueId,
    Instant startDateTime,
    Instant endDateTime,
    String status,
    Boolean active
) {}
