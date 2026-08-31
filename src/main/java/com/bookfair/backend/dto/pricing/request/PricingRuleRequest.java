package com.bookfair.backend.dto.pricing.request;

import java.math.BigDecimal;

import com.bookfair.backend.model.enums.ConditionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PricingRuleRequest(
    @NotBlank
    String name,

    @NotBlank
    String description,

    @NotBlank
    ConditionType conditionType,

    @NotBlank
    String conditionValue,

    @NotNull
    BigDecimal multiplier
) {}
