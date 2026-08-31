package com.bookfair.backend.dto.common;

import java.util.UUID;
public record LayoutMarkerDto(
    UUID id,
    String label,
    String type,
    Boolean primaryMarker,
    Boolean active,
    LayoutPositionDto layout
) {}
