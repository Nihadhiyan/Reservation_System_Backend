package com.bookfair.backend.dto.common.Mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bookfair.backend.dto.common.LayoutMarkerDto;
import com.bookfair.backend.dto.common.LayoutPositionDto;
import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.dto.layout.request.CreateLayoutMarkerRequest;
import com.bookfair.backend.model.Building;
import com.bookfair.backend.model.Hall;
import com.bookfair.backend.model.LayoutMarker;
import com.bookfair.backend.model.LayoutPosition;
import com.bookfair.backend.model.Venue;

@Mapper(config = GlobalMapperConfig.class)
public interface CommonMapper {
    LayoutPositionDto toLayoutPositionDto(LayoutPosition layoutPosition);

    LayoutMarkerDto toLayoutMarkerDto(LayoutMarker marker);

    List<LayoutMarkerDto> toLayoutMarkerDtos(List<LayoutMarker> markers);

    LayoutPosition toLayoutPosition(LayoutPositionDto dto);

    LayoutPosition toLayoutPositionFromCoords(Integer xCoord, Integer yCoord, Integer width, Integer height);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "type", source = "request.type")
    @Mapping(target = "label", source = "request.label")
    @Mapping(target = "primaryMarker", source = "request.primaryMarker")
    @Mapping(target = "layout", source = "layout")
    @Mapping(target = "venue", source = "venue")
    @Mapping(target = "building", source = "building")
    @Mapping(target = "hall", source = "hall")
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    LayoutMarker toLayoutMarker(CreateLayoutMarkerRequest request, LayoutPosition layout, Venue venue, Building building, Hall hall);
}

