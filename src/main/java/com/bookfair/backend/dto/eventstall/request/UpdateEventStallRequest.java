package com.bookfair.backend.dto.eventstall.request;

import com.bookfair.backend.dto.common.LayoutPositionDto;

import java.math.BigDecimal;

// Used by organizer to update an existing event stall configuration
// All fields are optional — only provided fields are updated
public record UpdateEventStallRequest(

    // Toggle stall on/off for this event
    Boolean activeForEvent,

    // Reposition the stall for this event
    LayoutPositionDto customLayout,

    // Relabel the stall for this event
    String customName,

    // Override the price for this stall in this event
    BigDecimal eventPrice
) {}
