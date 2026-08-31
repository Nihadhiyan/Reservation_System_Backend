package com.bookfair.backend.dto.venue.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VenueAvailabilityResponse(
    UUID venueId,
    String venueName,
    Instant requestedStart,
    Instant requestedEnd,
    boolean available,
    List<ConflictingEventDto> conflicts,
    List<HallAvailabilityDto> hallAvailability
) {}
