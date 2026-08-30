package com.bookfair.backend.dto.eventstall.response;

import com.bookfair.backend.dto.common.LayoutPositionDto;
import com.bookfair.backend.model.enums.AvailabilityStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record EventStallResponse(
    UUID id,
    UUID eventId,
    UUID stallId,
    String effectiveName,        // customName if set, else stall.name
    String originalStallName,    // always the venue owner's original name
    AvailabilityStatus availabilityStatus,
    boolean activeForEvent,
    LayoutPositionDto effectiveLayout, // customLayout if set, else stall.layout
    boolean isRepositioned,      // true if customLayout != null
    BigDecimal eventPrice,       // null if using PricingService fallback
    String hallName,
    UUID hallId
) {}
