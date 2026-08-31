package com.bookfair.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.venue.response.ConflictingEventDto;
import com.bookfair.backend.dto.venue.response.HallAvailabilityDto;
import com.bookfair.backend.dto.venue.response.VenueAvailabilityResponse;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.Hall;
import com.bookfair.backend.model.Venue;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.repository.HallRepository;
import com.bookfair.backend.repository.StallRepository;
import com.bookfair.backend.repository.VenueRepository;
import com.bookfair.backend.repository.EventSpaceBookingRepository;
import com.bookfair.backend.repository.BuildingRepository;
import com.bookfair.backend.repository.FloorRepository;
import com.bookfair.backend.model.Building;
import com.bookfair.backend.model.Floor;
import com.bookfair.backend.model.Stall;
import com.bookfair.backend.model.EventSpaceBooking;
import com.bookfair.backend.model.enums.BookingLevel;
import com.bookfair.backend.exception.ConflictDetail;
import com.bookfair.backend.dto.event.request.CreateEventSpaceBookingRequest;
import java.util.ArrayList;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VenueAvailabilityService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final HallRepository hallRepository;
    private final StallRepository stallRepository;
    private final EventSpaceBookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public VenueAvailabilityResponse checkVenueAvailability(
            UUID venueId, Instant requestedStart, Instant requestedEnd) {

        validateDateRange(requestedStart, requestedEnd);

        Venue venue = venueRepository.findByIdAndActiveTrue(venueId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Venue not found", ErrorCode.VENUE_NOT_FOUND));

        List<Event> conflicts = eventRepository
            .findConflictingEventsForVenue(venueId, requestedStart, requestedEnd);

        List<Hall> allHalls = hallRepository.findByFloorBuildingVenueIdAndActiveTrue(venueId);
        List<HallAvailabilityDto> hallAvailability = allHalls.stream()
            .map(hall -> buildHallAvailability(hall, requestedStart, requestedEnd))
            .toList();

        boolean isAvailable = conflicts.isEmpty();

        return new VenueAvailabilityResponse(
            venueId,
            venue.getName(),
            requestedStart,
            requestedEnd,
            isAvailable,
            conflicts.stream().map(this::toConflictDto).toList(),
            hallAvailability
        );
    }

    @Transactional(readOnly = true)
    public List<VenueAvailabilityResponse> findAvailableVenues(
            Instant requestedStart, Instant requestedEnd,
            Pageable pageable) {

        validateDateRange(requestedStart, requestedEnd);

        List<UUID> availableIds = eventRepository.findAvailableVenueIds(requestedStart, requestedEnd);

        return venueRepository.findAllByIdInAndActiveTrue(availableIds, pageable).stream()
            .map(venue -> buildSimpleAvailabilityResponse(venue, requestedStart, requestedEnd))
            .toList();
    }

    private VenueAvailabilityResponse buildSimpleAvailabilityResponse(Venue venue, Instant requestedStart, Instant requestedEnd) {
        return new VenueAvailabilityResponse(
            venue.getId(),
            venue.getName(),
            requestedStart,
            requestedEnd,
            true,
            List.of(),
            List.of()
        );
    }

    @Transactional(readOnly = true)
    public List<HallAvailabilityDto> getHallAvailabilityForVenue(
            UUID venueId, Instant requestedStart, Instant requestedEnd) {

        validateDateRange(requestedStart, requestedEnd);

        if (!venueRepository.existsByIdAndActiveTrue(venueId)) {
            throw new ResourceNotFoundException("Venue not found", ErrorCode.VENUE_NOT_FOUND);
        }

        List<Hall> allHalls = hallRepository.findByFloorBuildingVenueIdAndActiveTrue(venueId);
        return allHalls.stream()
            .map(hall -> buildHallAvailability(hall, requestedStart, requestedEnd))
            .toList();
    }

    private HallAvailabilityDto buildHallAvailability(
            Hall hall, Instant requestedStart, Instant requestedEnd) {

        List<Event> hallConflicts = eventRepository
            .findConflictingEventsForHall(hall.getId(), requestedStart, requestedEnd);

        long totalStalls = stallRepository.countByHallIdAndActiveTrue(hall.getId());

        return new HallAvailabilityDto(
            hall.getId(),
            hall.getName(),
            hall.getHallType(),
            hall.getSpaceCategory(),
            totalStalls,
            hallConflicts.isEmpty(),
            hallConflicts.isEmpty() ? null : hallConflicts.get(0).getName()
        );
    }

    private ConflictingEventDto toConflictDto(Event event) {
        return new ConflictingEventDto(
            event.getId(),
            event.getName(),
            event.getStartDateTime(),
            event.getEndDateTime(),
            event.getOrganizer() != null ? event.getOrganizer().getName() : null
        );
    }

    private void validateDateRange(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new BusinessException(
                "Start and end dates are required.", ErrorCode.VALIDATION_ERROR);
        }
        if (!start.isBefore(end)) {
            throw new BusinessException(
                "Start date must be before end date.", ErrorCode.VALIDATION_ERROR);
        }
        if (start.isBefore(Instant.now())) {
            throw new BusinessException(
                "Cannot check availability for past dates.", ErrorCode.VALIDATION_ERROR);
        }
        long durationDays = Duration.between(start, end).toDays();
        if (durationDays > 365) {
            throw new BusinessException(
                "Date range cannot exceed 365 days.", ErrorCode.VALIDATION_ERROR);
        }
    }

    private boolean hasItems(Set<UUID> set) {
        return set != null && !set.isEmpty();
    }

    @Transactional(readOnly = true)
    public List<ConflictDetail> checkMultiSpaceAvailability(UUID eventId, CreateEventSpaceBookingRequest request) {
        validateDateRange(request.startsAt(), request.endsAt());
        
        List<ConflictDetail> conflicts = new ArrayList<>();
        
        if (hasItems(request.venueIds())) {
            for (UUID venueId : request.venueIds()) {
                Venue venue = venueRepository.findByIdAndActiveTrue(venueId)
                    .orElseThrow(() -> new ResourceNotFoundException("Venue not found", ErrorCode.VENUE_NOT_FOUND));
                List<EventSpaceBooking> existing = bookingRepository.findConflictsForVenue(
                    eventId, venueId, request.startsAt(), request.endsAt());
                if (!existing.isEmpty()) {
                    conflicts.add(new ConflictDetail(BookingLevel.VENUE, venueId, venue.getName(), existing.get(0)));
                }
            }
        }

        if (hasItems(request.buildingIds())) {
            for (UUID buildingId : request.buildingIds()) {
                Building building = buildingRepository.findByIdAndActiveTrue(buildingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Building not found", ErrorCode.BUILDING_NOT_FOUND));
                List<EventSpaceBooking> existing = bookingRepository.findConflictsForBuilding(
                    eventId, building.getVenue().getId(), buildingId, request.startsAt(), request.endsAt());
                if (!existing.isEmpty()) {
                    conflicts.add(new ConflictDetail(BookingLevel.BUILDING, buildingId, building.getName(), existing.get(0)));
                }
            }
        }

        if (hasItems(request.floorIds())) {
            for (UUID floorId : request.floorIds()) {
                Floor floor = floorRepository.findByIdAndActiveTrue(floorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Floor not found", ErrorCode.FLOOR_NOT_FOUND));
                List<EventSpaceBooking> existing = bookingRepository.findConflictsForFloor(
                    eventId, floor.getBuilding().getVenue().getId(), floor.getBuilding().getId(), floorId, request.startsAt(), request.endsAt());
                if (!existing.isEmpty()) {
                    conflicts.add(new ConflictDetail(BookingLevel.FLOOR, floorId, floor.getLevelName(), existing.get(0)));
                }
            }
        }

        if (hasItems(request.hallIds())) {
            for (UUID hallId : request.hallIds()) {
                Hall hall = hallRepository.findById(hallId)
                    .orElseThrow(() -> new ResourceNotFoundException("Hall not found", ErrorCode.HALL_NOT_FOUND));
                List<EventSpaceBooking> existing = bookingRepository.findConflictsForHall(
                    eventId, hall.getFloor().getBuilding().getVenue().getId(), hall.getFloor().getBuilding().getId(), hall.getFloor().getId(), hallId, request.startsAt(), request.endsAt());
                if (!existing.isEmpty()) {
                    conflicts.add(new ConflictDetail(BookingLevel.HALL, hallId, hall.getName(), existing.get(0)));
                }
            }
        }

        return conflicts;
    }
}
