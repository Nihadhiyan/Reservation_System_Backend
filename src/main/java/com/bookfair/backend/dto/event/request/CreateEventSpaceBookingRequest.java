package com.bookfair.backend.dto.event.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateEventSpaceBookingRequest(
    Set<UUID> venueIds,
    Set<UUID> buildingIds,
    Set<UUID> floorIds,
    Set<UUID> hallIds,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    String notes
) {
    public boolean hasAnySelection() {
        return hasItems(venueIds) || hasItems(buildingIds)
            || hasItems(floorIds) || hasItems(hallIds);
    }

    private boolean hasItems(Set<UUID> set) {
        return set != null && !set.isEmpty();
    }
}
