package com.bookfair.backend.dto.event.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateStallBookingRequest(
    @NotEmpty Set<UUID> stallIds,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    String notes
) {}
