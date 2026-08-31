package com.bookfair.backend.dto.layout.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GenerateStallGridRequest(
    @NotNull
    @Min(1)
    Integer rows,

    @NotNull
    @Min(1)
    Integer columns,

    @NotNull
    @Min(1)
    Integer stallWidth,

    @NotNull
    @Min(1)
    Integer stallLength,

    @NotNull
    @Min(0)
    Integer aisleWidth,

    @NotNull
    @Min(0)
    Integer startX,

    @NotNull
    @Min(0)
    Integer startY
) {}
