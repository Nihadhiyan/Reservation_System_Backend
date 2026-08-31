package com.bookfair.backend.dto.floor.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.bookfair.backend.dto.common.Mapper.CommonMapper;
import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.dto.floor.request.CreateFloorRequest;
import com.bookfair.backend.dto.floor.request.UpdateFloorRequest;
import com.bookfair.backend.dto.floor.response.FloorResponse;
import com.bookfair.backend.model.Building;
import com.bookfair.backend.model.Floor;

@Mapper(
    config = GlobalMapperConfig.class,
    uses = {CommonMapper.class}
)
public interface FloorMapper {
    FloorResponse toFloorResponse(Floor floor);

    Floor toFloorFromCreateFloorRequest(CreateFloorRequest request);

    Floor updateFloorFromFloorRequest(UpdateFloorRequest request, @MappingTarget Floor floor);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "levelName", source = "request.levelName")
    @Mapping(target = "levelNumber", source = "request.levelNumber")
    @Mapping(target = "building", source = "building")
    Floor toFloor(CreateFloorRequest request, Building building);
}

