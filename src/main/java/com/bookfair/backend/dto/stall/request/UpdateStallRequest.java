package com.bookfair.backend.dto.stall.request;

import java.util.UUID;

import com.bookfair.backend.dto.common.LayoutPositionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateStallRequest(
    @NotNull(message = "Hall id is required")
    UUID hallId,

    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Stall type is required")
    String stallType,

    @Valid
    @NotNull(message = "Layout is required")
    LayoutPositionDto layout,

    @NotNull(message = "Square footage is required")
    Double squareFootage,

    @NotNull(message = "Active is required")
    Boolean active
) {}
