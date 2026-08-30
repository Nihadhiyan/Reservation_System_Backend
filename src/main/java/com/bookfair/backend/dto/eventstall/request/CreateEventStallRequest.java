package com.bookfair.backend.dto.eventstall.request;

import com.bookfair.backend.dto.common.LayoutPositionDto;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

// Used by organizer to add a specific stall to their event
// with optional customization
public record CreateEventStallRequest(

    @NotNull(message = "Stall ID is required")
    UUID stallId,

    // Optional customizations — all default to original stall values if null
    Boolean activeForEvent,       // defaults to true
    String customName,            // null = use original stall name
    LayoutPositionDto customLayout, // null = use original stall position
    BigDecimal eventPrice         // null = use PricingService fallback
) {}
