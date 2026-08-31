package com.bookfair.backend.dto.eventstall.mapper;

import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.dto.eventstall.response.EventStallResponse;
import com.bookfair.backend.model.EventStall;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface EventStallMapper {

    @Mapping(target = "eventId", source = "event.id")
    @Mapping(target = "stallId", source = "stall.id")
    @Mapping(target = "effectiveName",
        expression = "java(eventStall.getEffectiveName())")
    @Mapping(target = "originalStallName", source = "stall.name")
    @Mapping(target = "effectiveLayout",
        expression = "java(eventStall.getEffectiveLayout() != null ? new com.bookfair.backend.dto.common.LayoutPositionDto(eventStall.getEffectiveLayout().getXCoord(), eventStall.getEffectiveLayout().getYCoord(), eventStall.getEffectiveLayout().getWidth(), eventStall.getEffectiveLayout().getHeight()) : null)")
    @Mapping(target = "isRepositioned",
        expression = "java(eventStall.getCustomLayout() != null)")
    @Mapping(target = "hallName", source = "stall.hall.name")
    @Mapping(target = "hallId", source = "stall.hall.id")
    EventStallResponse toEventStallResponse(EventStall eventStall);
}
