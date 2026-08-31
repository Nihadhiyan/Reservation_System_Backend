package com.bookfair.backend.dto.event.response;

import com.bookfair.backend.model.enums.BookingLevel;
import com.bookfair.backend.model.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventSpaceBookingResponse(
    UUID id,
    UUID eventId,
    BookingLevel bookingLevel,
    UUID venueId,
    UUID buildingId,
    UUID floorId,
    UUID hallId,
    UUID stallId,
    BookingStatus status,
    BigDecimal price,
    Instant startsAt,
    Instant endsAt
) {}
