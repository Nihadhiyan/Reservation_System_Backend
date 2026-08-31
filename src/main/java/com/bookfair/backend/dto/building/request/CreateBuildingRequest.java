package com.bookfair.backend.dto.building.request;

import java.util.UUID;

import com.bookfair.backend.dto.common.LayoutPositionDto;
import com.bookfair.backend.model.enums.BuildingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBuildingRequest(
    @NotNull(message = "Venue id is required")
    UUID venueId,

    @NotBlank(message = "Name is required")
    String name,

    @Valid
    @NotNull(message = "Layout is required")
    LayoutPositionDto layout,

    @NotNull(message = "Square footage is required")
    Double squareFootage,

    @NotNull(message = "Type is required")
    BuildingType type
) {}
