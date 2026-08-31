package com.bookfair.backend.dto.reservation.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.bookfair.backend.dto.common.SimpleEventDto;
import com.bookfair.backend.dto.common.SimpleUserDto;
public record ReservationSummaryResponse(
    UUID id,
    SimpleUserDto user,
    SimpleEventDto bookFair,
    LocalDate date,
    String status,
    Integer totalStalls,
    BigDecimal totalAmount
) {}
