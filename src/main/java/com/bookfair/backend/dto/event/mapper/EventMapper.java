package com.bookfair.backend.dto.event.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.dto.event.request.CreateEventRequest;
import com.bookfair.backend.dto.event.response.EventResponse;
import com.bookfair.backend.dto.event.response.EventSummaryResponse;
import com.bookfair.backend.dto.organization.mapper.OrganizationMapper;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.Venue;

@Mapper(
    config = GlobalMapperConfig.class,
    uses = {OrganizationMapper.class}
)
public interface EventMapper {
    EventResponse toEventResponse(Event event);

    EventSummaryResponse toEventSummaryResponse(Event event);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizer", source = "organizer")
    @Mapping(target = "venue", source = "venue")
    @Mapping(target = "partners", source = "partners")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "eventType", source = "request.eventType")
    @Mapping(target = "startDateTime", source = "request.startDateTime")
    @Mapping(target = "endDateTime", source = "request.endDateTime")
    @Mapping(target = "status", source = "request.status")
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    Event toEvent(CreateEventRequest request, Organization organizer, Venue venue, List<Organization> partners);

}
