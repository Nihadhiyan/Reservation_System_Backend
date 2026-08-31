package com.bookfair.backend.dto.venue.response;

import java.util.UUID;
import com.bookfair.backend.model.enums.HallType;
import com.bookfair.backend.model.enums.SpaceCategory;

public record HallAvailabilityDto(
    UUID hallId,
    String hallName,
    HallType hallType,
    SpaceCategory spaceCategory,
    long totalStalls,
    boolean available,
    String conflictingEventName
) {}
