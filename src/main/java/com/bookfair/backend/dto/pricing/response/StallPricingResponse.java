package com.bookfair.backend.dto.pricing.response;

import java.math.BigDecimal;
import java.util.UUID;
public record StallPricingResponse(
    UUID id,
    UUID stallId,
    String stallName,
    String hallName,
    BigDecimal basePrice,
    BigDecimal manualOverridePrice,
    BigDecimal finalPrice,
    String status
) {}
