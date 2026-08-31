package com.bookfair.backend.exception;

import com.bookfair.backend.model.enums.BookingLevel;
import com.bookfair.backend.model.EventSpaceBooking;
import java.util.UUID;

public record ConflictDetail(
    BookingLevel level,
    UUID spaceId,
    String spaceName,
    EventSpaceBooking conflictingBooking
) {}
