package com.bookfair.backend.dto.analytics.response;

import java.math.BigDecimal;

public record RevenueAnalyticsResponse(
    BigDecimal totalRevenue,
    Long completedPayments,
    Long failedPayments,
    Long refundedPayments,
    String currency
) {}
