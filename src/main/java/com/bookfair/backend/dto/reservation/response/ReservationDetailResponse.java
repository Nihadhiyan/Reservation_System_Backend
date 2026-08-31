package com.bookfair.backend.dto.reservation.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.bookfair.backend.dto.common.SimpleEventDto;
import com.bookfair.backend.dto.common.SimpleUserDto;
public record ReservationDetailResponse(
    UUID id,
    SimpleUserDto user,
    SimpleEventDto event,
    LocalDate date,
    Instant reservationStartDateTime,
    Instant expiresAt,
    LocalTime time,
    String status,
    BigDecimal totalAmount,
    List<ReservationStallResponse> stalls,
    UUID organizationId,
    String organizationName,
    UUID reservationCreatedByUserId,
    String reservationCreatedByUsername
) {}
