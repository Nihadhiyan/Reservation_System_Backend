package com.bookfair.backend.dto.analytics.response;

public record DashboardSummaryResponse(
    ReservationAnalyticsResponse reservationAnalytics,
    RevenueAnalyticsResponse revenueAnalytics
) {}
