package com.bookfair.backend.dto.pricing.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record PricingBreakdownResponse(
    UUID reservationId,
    String eventName,
    List<StallPricingResponse> stalls,
    BigDecimal subtotal,
    BigDecimal discountAmount,
    BigDecimal taxAmount,
    BigDecimal total,
    String currency
) {}
