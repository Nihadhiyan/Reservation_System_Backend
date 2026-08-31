package com.bookfair.backend.dto.reservation.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.bookfair.backend.dto.common.SimpleStallDto;
public record ReservationStallResponse(
    UUID id,
    SimpleStallDto stall,
    BigDecimal priceAtBooking
) {}
