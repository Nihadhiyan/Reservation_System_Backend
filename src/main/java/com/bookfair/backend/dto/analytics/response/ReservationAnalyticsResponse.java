package com.bookfair.backend.dto.analytics.response;

public record ReservationAnalyticsResponse(
    Long totalReservations,
    Long confirmedReservations,
    Long cancelledReservations,
    Long pendingReservations
) {}
