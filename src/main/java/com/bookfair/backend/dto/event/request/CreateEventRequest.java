package com.bookfair.backend.dto.event.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bookfair.backend.model.enums.EventStatus;
import com.bookfair.backend.model.enums.EventType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEventRequest(
    @NotBlank(message = "Event name is required")
    String name,

    @NotNull(message = "Venue ID is required")
    UUID venueId,

    @NotNull(message = "Organizer ID is required")
    UUID organizerId,

    List<UUID> partnerIds,

    @NotNull(message = "Event type is required")
    EventType eventType,

    @NotNull(message = "Event start date is required")
    Instant startDateTime,

    @NotNull(message = "Event end date is required")
    Instant endDateTime,

    @NotNull(message = "Event status is required")
    EventStatus status
) {}
