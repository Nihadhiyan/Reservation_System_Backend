package com.bookfair.backend.dto.event.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.dto.event.response.EventSpaceBookingResponse;
import com.bookfair.backend.model.EventSpaceBooking;

@Mapper(config = GlobalMapperConfig.class)
public interface EventSpaceBookingMapper {

    @Mapping(target = "eventId", source = "event.id")
    @Mapping(target = "venueId", source = "venue.id")
    @Mapping(target = "buildingId", source = "building.id")
    @Mapping(target = "floorId", source = "floor.id")
    @Mapping(target = "hallId", source = "hall.id")
    @Mapping(target = "stallId", source = "stall.id")
    EventSpaceBookingResponse toResponse(EventSpaceBooking booking);
}
