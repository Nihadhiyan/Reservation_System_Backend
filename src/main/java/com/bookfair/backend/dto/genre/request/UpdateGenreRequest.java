package com.bookfair.backend.dto.genre.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateGenreRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotNull(message = "Active is required")
    Boolean active,

    @NotBlank(message = "Color is required")
    String color
) {}
