package com.bookfair.backend.dto.hall.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.bookfair.backend.dto.common.Mapper.CommonMapper;
import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.dto.hall.request.CreateHallRequest;
import com.bookfair.backend.dto.hall.request.UpdateHallRequest;
import com.bookfair.backend.dto.hall.response.HallLayoutResponse;
import com.bookfair.backend.dto.hall.response.HallResponse;
import com.bookfair.backend.model.Floor;
import com.bookfair.backend.model.Hall;

@Mapper(
    config = GlobalMapperConfig.class,
    uses = {CommonMapper.class}
)
public interface HallMapper {
    HallResponse toHallResponse(Hall hall);

    HallLayoutResponse toHallLayoutResponse(Hall hall);

    Hall toHallFromCreateHallRequest(CreateHallRequest request);

    Hall UpdateHallFromHallRequest(UpdateHallRequest request, @MappingTarget Hall hall);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "floor", source = "floor")
    @Mapping(target = "layout", source = "request.layout")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "spaceCategory", source = "request.spaceCategory")
    @Mapping(target = "hallType", source = "request.hallType")
    @Mapping(target = "blueprintImageUrl", source = "request.blueprintImageUrl")
    @Mapping(target = "squareFootage", source = "request.squareFootage")
    @Mapping(target = "maxStalls", source = "request.maxStalls")
    @Mapping(target = "wifiAvailable", source = "request.wifiAvailable")
    @Mapping(target = "airConditioned", source = "request.airConditioned")
    Hall toHall(CreateHallRequest request, Floor floor);
}

