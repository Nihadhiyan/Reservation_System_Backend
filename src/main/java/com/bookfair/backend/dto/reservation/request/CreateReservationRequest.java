package com.bookfair.backend.dto.reservation.request;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

public record CreateReservationRequest(
    @NotNull(message = "Event id is required")
    UUID eventId,

    Set<UUID> venueIds,
    Set<UUID> buildingIds,
    Set<UUID> floorIds,
    Set<UUID> hallIds,
    Set<UUID> stallIds,

    @NotNull(message = "Reservation start time is required")
    @FutureOrPresent(message = "Reservation start time cannot be in the past")
    Instant reservationStartDateTime,

    @NotNull(message = "Expiration time is required")
    Instant expiresAt,

    @NotNull(message = "Genre id is required")
    UUID genreId,

    @NotNull(message = "Organization id is required")
    UUID organizationId
) {
    public boolean hasAnySelection() {
        return (venueIds != null && !venueIds.isEmpty()) ||
               (buildingIds != null && !buildingIds.isEmpty()) ||
               (floorIds != null && !floorIds.isEmpty()) ||
               (hallIds != null && !hallIds.isEmpty()) ||
               (stallIds != null && !stallIds.isEmpty());
    }
}
