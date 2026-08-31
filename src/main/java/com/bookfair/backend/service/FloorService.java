package com.bookfair.backend.service;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.floor.mapper.FloorMapper;
import com.bookfair.backend.dto.floor.request.CreateFloorRequest;
import com.bookfair.backend.dto.floor.request.UpdateFloorRequest;
import com.bookfair.backend.dto.floor.response.FloorResponse;
import com.bookfair.backend.dto.hall.mapper.HallMapper;
import com.bookfair.backend.dto.hall.response.HallResponse;
import com.bookfair.backend.event.hierarchy.FloorDeactivatedEvent;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.Building;
import com.bookfair.backend.model.Floor;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.repository.EventSpaceBookingRepository;
import com.bookfair.backend.repository.FloorRepository;
import com.bookfair.backend.repository.BuildingRepository;
import com.bookfair.backend.repository.HallRepository;
import static java.util.Objects.requireNonNull;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FloorService {

    private final FloorRepository floorRepository;
    private final BuildingRepository buildingRepository;
    private final HallRepository hallRepository;
    private final EventRepository eventRepository;
    private final EventSpaceBookingRepository bookingRepository;
    private final FloorMapper floorMapper;
    private final HallMapper hallMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FloorResponse createFloor(CreateFloorRequest request) {
        requireNonNull(request, "request cannot be null");
        Building building = buildingRepository.findById(requireNonNull(request.buildingId()))
                .orElseThrow(() -> new ResourceNotFoundException("Building not found", ErrorCode.BUILDING_NOT_FOUND));

        if (!Boolean.TRUE.equals(building.getActive())) {
            throw new BusinessException("Cannot create a Floor under an inactive Building.", ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        Floor floor = floorMapper.toFloor(request, building);
        floor.setActive(true);

        Floor saved = floorRepository.save(floor);
        return floorMapper.toFloorResponse(saved);
    }

    @Transactional(readOnly = true)
    public FloorResponse getFloorById(UUID id) {
        Floor floor = floorRepository.findByIdAndActiveTrue(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found", ErrorCode.FLOOR_NOT_FOUND));

        return floorMapper.toFloorResponse(floor);
    }

    @Transactional
    public FloorResponse updateFloor(UUID id, UpdateFloorRequest request) {
        requireNonNull(request, "request cannot be null");
        Floor floor = floorRepository.findById(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found", ErrorCode.FLOOR_NOT_FOUND));

        Building building = buildingRepository.findById(requireNonNull(request.buildingId()))
                .orElseThrow(() -> new ResourceNotFoundException("Building not found", ErrorCode.BUILDING_NOT_FOUND));

        if (!floor.getBuilding().getId().equals(building.getId()) && !Boolean.TRUE.equals(building.getActive())) {
            throw new BusinessException("Cannot move Floor to an inactive Building.", ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        if (!floor.getBuilding().getId().equals(building.getId())) {
            UUID oldVenueId = floor.getBuilding().getVenue().getId();
            UUID newVenueId = building.getVenue().getId();
            if (!oldVenueId.equals(newVenueId)) {
                if (bookingRepository.countActiveByFloorId(floor.getId(), Instant.now()) > 0) {
                    throw new BusinessException("Cannot move Floor across Venues because stalls are assigned to Events in the original Venue.", ErrorCode.BUSINESS_RULE_VIOLATION);
                }
            }
        }

        boolean oldActive = Boolean.TRUE.equals(floor.getActive());
        if (request.active() != null && !request.active() && oldActive) {
            validateNoActiveBookingsForFloor(floor.getId(), floor.getLevelName());
        }

        floorMapper.updateFloorFromFloorRequest(request, floor);
        floor.setBuilding(building);

        Floor saved = floorRepository.save(floor);
        eventPublisher.publishEvent(new com.bookfair.backend.event.hierarchy.FloorUpdatedEvent(saved.getId()));

        if (oldActive && !Boolean.TRUE.equals(saved.getActive())) {
            eventPublisher.publishEvent(new FloorDeactivatedEvent(saved.getId()));
        }

        return floorMapper.toFloorResponse(saved);
    }

    @Transactional
    public void deleteFloor(UUID id) {
        Floor floor = floorRepository.findById(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found", ErrorCode.FLOOR_NOT_FOUND));

        validateNoActiveBookingsForFloor(floor.getId(), floor.getLevelName());

        floor.setActive(false);
        floorRepository.save(floor);
        eventPublisher.publishEvent(new FloorDeactivatedEvent(floor.getId()));
    }

    private void validateNoActiveBookingsForFloor(UUID floorId, String floorName) {
        UUID venueId = floorRepository.findVenueIdByFloorId(floorId)
            .orElseThrow(() -> new ResourceNotFoundException("Floor's venue not found", ErrorCode.VENUE_NOT_FOUND));

        List<Event> upcoming = eventRepository
            .findUpcomingOrOngoingEventsForVenue(venueId, Instant.now());

        if (!upcoming.isEmpty()) {
            Event next = upcoming.get(0);
            throw new BusinessException(
                "Cannot deactivate floor '" + floorName 
                + "' — event '" + next.getName() 
                + "' is scheduled at this venue until " 
                + next.getEndDateTime() + ".", 
                ErrorCode.BUSINESS_RULE_VIOLATION);
        }
    }

    @Transactional(readOnly = true)
    public List<HallResponse> getHallsByFloor(UUID floorId) {
        if (!floorRepository.existsByIdAndActiveTrue(requireNonNull(floorId))) {
            throw new ResourceNotFoundException("Floor not found", ErrorCode.FLOOR_NOT_FOUND);
        }

        return hallRepository.findByFloorIdAndActiveTrue(floorId).stream()
                .map(hallMapper::toHallResponse)
                .toList();
    }
}
