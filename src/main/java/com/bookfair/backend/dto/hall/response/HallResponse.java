package com.bookfair.backend.dto.hall.response;

import java.util.UUID;

import com.bookfair.backend.dto.common.LayoutPositionDto;
public record HallResponse(
    UUID id,
    String name,
    String spaceCategory,
    String hallType,
    LayoutPositionDto layout,
    String blueprintImageUrl,
    Double squareFootage,
    Boolean active,
    Integer maxStalls,
    Integer currentStallCount,
    Boolean wifiAvailable,
    Boolean airConditioned
) {}
