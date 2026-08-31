package com.bookfair.backend.dto.building.response;

import java.util.UUID;

import com.bookfair.backend.dto.common.LayoutPositionDto;
public record BuildingResponse(
    UUID id,
    String name,
    LayoutPositionDto layoutPosition,
    Double squareFootage,
    Integer numberOfFloors,
    String type,
    Boolean active
) {}
