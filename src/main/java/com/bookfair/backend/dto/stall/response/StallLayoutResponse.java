package com.bookfair.backend.dto.stall.response;

import java.util.UUID;

import com.bookfair.backend.dto.common.LayoutPositionDto;
public record StallLayoutResponse(
    UUID id,
    String name,
    String stallType,
    LayoutPositionDto layout,
    Boolean active
) {}
