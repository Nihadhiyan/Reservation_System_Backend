package com.bookfair.backend.dto.pricing.response;

import java.math.BigDecimal;
import java.util.UUID;
import com.bookfair.backend.model.enums.ConditionType;

public record PricingRuleResponse(
    UUID id,
    String name,
    String description,
    ConditionType conditionType,
    String conditionValue,
    BigDecimal multiplier,
    Boolean active
) {}
