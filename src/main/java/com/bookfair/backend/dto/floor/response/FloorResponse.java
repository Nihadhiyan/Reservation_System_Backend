package com.bookfair.backend.dto.floor.response;

import java.util.UUID;
public record FloorResponse(
    UUID id,
    String levelName,
    Integer levelNumber
) {}
