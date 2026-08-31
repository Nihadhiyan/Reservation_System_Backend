package com.bookfair.backend.dto.admin.response;

import java.math.BigDecimal;

public record AdminDashboardResponse(
    long totalUsers,
    long totalStalls,
    long activeReservations,
    BigDecimal totalRevenue
) {}
