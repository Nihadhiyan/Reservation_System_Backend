package com.bookfair.backend.dto.venue.response;

import java.util.List;
import java.util.UUID;

import com.bookfair.backend.dto.building.response.BuildingResponse;
import com.bookfair.backend.dto.common.LayoutMarkerDto;
public record VenueMapResponse(
    UUID id,
    String name,
    String address,
    List<LayoutMarkerDto> markers,
    List<BuildingResponse> buildings
) {}
