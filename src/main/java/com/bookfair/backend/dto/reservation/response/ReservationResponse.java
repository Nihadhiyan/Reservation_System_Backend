package com.bookfair.backend.dto.reservation.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.math.BigDecimal;

import com.bookfair.backend.dto.common.SimpleEventDto;
import com.bookfair.backend.dto.common.SimpleUserDto;
public record ReservationResponse(
    UUID id,
    SimpleUserDto user,
    SimpleEventDto event,
    LocalDate date,
    Instant reservationStartDateTime,
    Instant expiresAt,
    LocalTime time,
    String status,
    UUID genreId,
    String qrCodePayload,
    UUID organizationId,
    String organizationName,
    UUID reservationCreatedByUserId,
    String reservationCreatedByUsername,
    BigDecimal totalPrice,
    BigDecimal totalAmount
) {}
