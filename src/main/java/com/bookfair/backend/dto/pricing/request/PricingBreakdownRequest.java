package com.bookfair.backend.dto.pricing.request;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record PricingBreakdownRequest(
    @NotNull(message = "Reservation id is required")
    UUID reservationId,

    @NotNull(message = "Discount amount is required")
    BigDecimal discountAmount,

    @NotNull(message = "Tax amount is required")
    BigDecimal taxAmount
) {}
