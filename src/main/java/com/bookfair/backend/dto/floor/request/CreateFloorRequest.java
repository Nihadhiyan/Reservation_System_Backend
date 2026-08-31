package com.bookfair.backend.dto.floor.request;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateFloorRequest(
    @NotNull(message = "Building id is required")
    UUID buildingId,

    @NotBlank(message = "Level name is required")
    String levelName,

    @NotNull(message = "Level number is required")
    Integer levelNumber
) {}
