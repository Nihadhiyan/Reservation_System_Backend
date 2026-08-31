package com.bookfair.backend.service;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.building.mapper.BuildingMapper;
import com.bookfair.backend.dto.building.request.CreateBuildingRequest;
import com.bookfair.backend.dto.building.request.UpdateBuildingRequest;
import com.bookfair.backend.dto.building.response.BuildingResponse;
import com.bookfair.backend.dto.floor.mapper.FloorMapper;
import com.bookfair.backend.dto.floor.response.FloorResponse;
import com.bookfair.backend.event.hierarchy.BuildingDeactivatedEvent;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.Building;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.Venue;
import com.bookfair.backend.repository.BuildingRepository;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.repository.EventSpaceBookingRepository;
import com.bookfair.backend.repository.FloorRepository;
import com.bookfair.backend.repository.VenueRepository;
import static java.util.Objects.requireNonNull;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final VenueRepository venueRepository;
    private final FloorRepository floorRepository;
    private final EventRepository eventRepository;
    private final EventSpaceBookingRepository bookingRepository;
    private final BuildingMapper buildingMapper;
    private final FloorMapper floorMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public BuildingResponse createBuilding(CreateBuildingRequest request) {
        requireNonNull(request, "request cannot be null");
        Venue venue = venueRepository.findById(requireNonNull(request.venueId()))
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found", ErrorCode.VENUE_NOT_FOUND));

        if (!Boolean.TRUE.equals(venue.getActive())) {
            throw new BusinessException("Cannot create a Building under an inactive Venue.", ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        Building building = buildingMapper.toBuildingFromCreateBuildingRequest(request);
        building.setVenue(venue);
        building.setActive(true);
        building.setType(request.type());

        Building saved = buildingRepository.save(building);
        return buildingMapper.toBuildingResponse(saved);
    }

    @Transactional(readOnly = true)
    public BuildingResponse getBuildingById(UUID id) {
        Building building = buildingRepository.findByIdAndActiveTrue(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Building not found", ErrorCode.BUILDING_NOT_FOUND));

        return buildingMapper.toBuildingResponse(building);
    }

    @Transactional
    public BuildingResponse updateBuilding(UUID id, UpdateBuildingRequest request) {
        requireNonNull(request, "request cannot be null");
        Building building = buildingRepository.findById(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Building not found", ErrorCode.BUILDING_NOT_FOUND));

        Venue venue = venueRepository.findById(requireNonNull(request.venueId()))
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found", ErrorCode.VENUE_NOT_FOUND));

        if (!building.getVenue().getId().equals(venue.getId()) && !Boolean.TRUE.equals(venue.getActive())) {
            throw new BusinessException("Cannot move Building to an inactive Venue.", ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        if (!building.getVenue().getId().equals(venue.getId())) {
            long count = bookingRepository.countActiveByBuildingId(building.getId(), Instant.now());
            if (count > 0) {
                throw new BusinessException("Cannot move Building across Venues because stalls are assigned to Events in the original Venue.", ErrorCode.BUSINESS_RULE_VIOLATION);
            }
        }

        boolean oldActive = Boolean.TRUE.equals(building.getActive());
        if (request.active() != null && !request.active() && oldActive) {
            validateNoActiveBookingsForBuilding(building.getId(), building.getName());
        }

        buildingMapper.updateBuildingFromBuildingRequest(request, building);
        building.setVenue(venue);
        building.setType(request.type());

        Building saved = buildingRepository.save(building);

        if (oldActive && !Boolean.TRUE.equals(saved.getActive())) {
            eventPublisher.publishEvent(new BuildingDeactivatedEvent(saved.getId()));
        }

        return buildingMapper.toBuildingResponse(saved);
    }

    @Transactional
    public void deleteBuilding(UUID id) {
        Building building = buildingRepository.findById(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Building not found", ErrorCode.BUILDING_NOT_FOUND));

        validateNoActiveBookingsForBuilding(building.getId(), building.getName());

        building.setActive(false);
        buildingRepository.save(building);
        eventPublisher.publishEvent(new BuildingDeactivatedEvent(building.getId()));
    }

    private void validateNoActiveBookingsForBuilding(UUID buildingId, String buildingName) {
        UUID venueId = buildingRepository.findVenueIdByBuildingId(buildingId)
            .orElseThrow(() -> new ResourceNotFoundException("Building's venue not found", ErrorCode.VENUE_NOT_FOUND));

        List<Event> upcoming = eventRepository
            .findUpcomingOrOngoingEventsForVenue(venueId, Instant.now());

        if (!upcoming.isEmpty()) {
            Event next = upcoming.get(0);
            throw new BusinessException(
                "Cannot deactivate building '" + buildingName 
                + "' — event '" + next.getName() 
                + "' is scheduled at this venue until " 
                + next.getEndDateTime() + ".", 
                ErrorCode.BUSINESS_RULE_VIOLATION);
        }
    }

    @Transactional(readOnly = true)
    public List<FloorResponse> getFloorsByBuilding(UUID buildingId) {
        if (!buildingRepository.existsById(requireNonNull(buildingId))) {
            throw new ResourceNotFoundException("Building not found", ErrorCode.BUILDING_NOT_FOUND);
        }

        return floorRepository.findByBuildingIdOrderByLevelNumberAsc(buildingId).stream()
                .map(floorMapper::toFloorResponse)
                .toList();
    }
}
