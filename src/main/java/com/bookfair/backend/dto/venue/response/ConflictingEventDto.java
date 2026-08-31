package com.bookfair.backend.dto.venue.response;

import java.time.Instant;
import java.util.UUID;

public record ConflictingEventDto(
    UUID eventId,
    String eventName,
    Instant eventStart,
    Instant eventEnd,
    String organizerName
) {}
