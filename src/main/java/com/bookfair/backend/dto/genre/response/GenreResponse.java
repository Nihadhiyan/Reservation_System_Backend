package com.bookfair.backend.dto.genre.response;

import java.util.UUID;

public record GenreResponse(
    UUID id,
    String name,
    Boolean active,
    String color
) {}
