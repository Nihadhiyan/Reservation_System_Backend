package com.bookfair.backend.dto.reservation.request;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record UpdateReservationRequest(
    @NotNull(message = "User id is required")
    UUID userId,

    @NotNull(message = "Book fair id is required")
    UUID eventId,

    @NotEmpty(message = "At least one stall id is required")
    List<UUID> stallIds,

    @NotNull(message = "Date is required")
    LocalDate date,

    @NotNull(message = "Reservation start time is required")
    Instant reservationStartDateTime,

    @NotNull(message = "Expiration time is required")
    Instant expiresAt,

    @NotNull(message = "Time is required")
    LocalTime time,

    @NotBlank(message = "Status is required")
    String status,

    @NotNull(message = "Genre id is required")
    UUID genreId,

    @NotBlank(message = "Qr code payload is required")
    String qrCodePayload
) {}
