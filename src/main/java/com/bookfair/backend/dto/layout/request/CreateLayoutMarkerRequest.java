package com.bookfair.backend.dto.layout.request;

import java.util.UUID;

import com.bookfair.backend.dto.common.LayoutPositionDto;
import com.bookfair.backend.model.enums.FeatureType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateLayoutMarkerRequest(
    UUID venueId,
    UUID buildingId,
    UUID hallId,

    @NotNull
    FeatureType type,

    @NotBlank
    String label,

    @NotNull
    Boolean primaryMarker,

    @Valid
    @NotNull
    LayoutPositionDto layout
) {}
