package com.bookfair.backend.dto.hall.response;

import java.util.List;
import java.util.UUID;

import com.bookfair.backend.dto.common.LayoutMarkerDto;
import com.bookfair.backend.dto.stall.response.StallResponse;

public record HallLayoutResponse(
    UUID id,
    String name,
    String spaceCategory,
    String hallType,
    List<LayoutMarkerDto> markers,
    List<StallResponse> stalls
) {}
