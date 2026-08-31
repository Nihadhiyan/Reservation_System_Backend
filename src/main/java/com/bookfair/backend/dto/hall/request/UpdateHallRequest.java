package com.bookfair.backend.dto.hall.request;

import java.util.UUID;

import com.bookfair.backend.dto.common.LayoutPositionDto;
import com.bookfair.backend.model.enums.HallType;
import com.bookfair.backend.model.enums.SpaceCategory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateHallRequest(
    @NotNull(message = "Floor Id is required")
    UUID floorId,

    @NotBlank(message = "Name is required")
    String name,

    @NotNull(message = "Space category is required")
    SpaceCategory spaceCategory,

    @NotNull(message = "Hall type is required")
    HallType hallType,

    @Valid
    @NotNull(message = "Layout is required")
    LayoutPositionDto layout,

    @NotBlank(message = "Blueprint image url is required")
    String blueprintImageUrl,

    @NotNull(message = "Square footage is required")
    Double squareFootage,

    @NotNull(message = "Max stalls is required")
    Integer maxStalls,

    @NotNull(message = "Wifi available is required")
    Boolean wifiAvailable,

    @NotNull(message = "Air conditioned is required")
    Boolean airConditioned,

    @NotNull(message = "Active is required")
    Boolean active
) {}
