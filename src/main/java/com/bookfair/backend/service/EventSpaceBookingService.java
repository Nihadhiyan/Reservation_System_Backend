package com.bookfair.backend.service;

import com.bookfair.backend.dto.event.request.CreateEventSpaceBookingRequest;
import com.bookfair.backend.dto.event.request.CreateStallBookingRequest;
import com.bookfair.backend.dto.event.response.EventSpaceBookingResponse;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ConflictDetail;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ForbiddenException;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.exception.SpaceConflictException;
import com.bookfair.backend.model.*;
import com.bookfair.backend.model.enums.BookingLevel;
import com.bookfair.backend.model.enums.BookingStatus;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.model.enums.SystemRole;
import com.bookfair.backend.model.enums.ReservationStatus;
import com.bookfair.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.bookfair.backend.dto.event.mapper.EventSpaceBookingMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventSpaceBookingService {

    private final EventSpaceBookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final HallRepository hallRepository;
    private final StallRepository stallRepository;
    private final PricingService pricingService;
    private final ApplicationEventPublisher eventPublisher;
    private final EventSpaceBookingMapper bookingMapper;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final EventStallService eventStallService;
    private final ReservationRepository reservationRepository;
    private final EventStallRepository eventStallRepository;

    @Transactional
    public List<EventSpaceBookingResponse> createSpaceBookings(
            UUID userId, UUID eventId, CreateEventSpaceBookingRequest request) {

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.USER_NOT_FOUND));

        BookingLevel level = resolveBookingLevel(request);

        if (level == BookingLevel.STALL) {
            throw new BusinessException(
                "Use the stall-bookings endpoint for stall-level bookings.",
                ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        validateBookingLevelPermission(user, level, eventId);

        validateDateRange(request.startsAt(), request.endsAt());

        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event not found", ErrorCode.EVENT_NOT_FOUND));

        List<EventSpaceBooking> bookings = new ArrayList<>();
        List<ConflictDetail> conflicts = new ArrayList<>();

        if (hasItems(request.venueIds())) {
            processVenueBookings(eventId, request, event, bookings, conflicts);
        }
        if (hasItems(request.buildingIds())) {
            processBuildingBookings(eventId, request, event, bookings, conflicts);
        }
        if (hasItems(request.floorIds())) {
            processFloorBookings(eventId, request, event, bookings, conflicts);
        }
        if (hasItems(request.hallIds())) {
            processHallBookings(eventId, request, event, bookings, conflicts);
        }

        if (!conflicts.isEmpty()) {
            throw new SpaceConflictException(
                "Some selected spaces are not available.", ErrorCode.SPACE_CONFLICT, conflicts);
        }

        List<EventSpaceBooking> saved = bookingRepository.saveAll(bookings);

        log.info("Created {} space bookings for event [{}] by user [{}]",
            saved.size(), eventId, userId);

        for (EventSpaceBooking booking : saved) {
            List<Stall> resolvedStalls = resolveStallsForBooking(booking);
            if (!resolvedStalls.isEmpty()) {
                eventStallService.generateEventStallsForEvent(eventId, resolvedStalls);
            }
        }

        return saved.stream()
            .map(bookingMapper::toResponse)
            .toList();
    }

    @Transactional
    public List<EventSpaceBookingResponse> createStallBookings(
            UUID userId, UUID eventId, CreateStallBookingRequest request) {

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.USER_NOT_FOUND));

        BookingLevel level = BookingLevel.STALL;

        validateBookingLevelPermission(user, level, eventId);

        validateDateRange(request.startsAt(), request.endsAt());

        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event not found", ErrorCode.EVENT_NOT_FOUND));

        List<EventSpaceBooking> bookings = new ArrayList<>();
        List<ConflictDetail> conflicts = new ArrayList<>();

        for (UUID stallId : request.stallIds()) {
            Stall stall = stallRepository.findById(stallId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Stall not found: " + stallId, ErrorCode.STALL_NOT_FOUND));

            List<EventSpaceBooking> existing = bookingRepository
                .findConflictsForStall(
                    eventId, stall.getHall().getFloor().getBuilding().getVenue().getId(), stall.getHall().getFloor().getBuilding().getId(), stall.getHall().getFloor().getId(), stall.getHall().getId(), stallId,
                    request.startsAt(), request.endsAt());

            if (!existing.isEmpty()) {
                conflicts.add(new ConflictDetail(
                    BookingLevel.STALL, stallId, stall.getName(),
                    existing.get(0)));
            } else {
                EventSpaceBooking b = new EventSpaceBooking();
                b.setEvent(event);
                b.setBookingLevel(BookingLevel.STALL);
                b.setStall(stall);
                b.setStatus(BookingStatus.PENDING);
                b.setPrice(pricingService.calculatePrice(BookingLevel.STALL, stallId, request.startsAt(), request.endsAt()));
                b.setStartsAt(request.startsAt());
                b.setEndsAt(request.endsAt());
                bookings.add(b);
            }
        }

        if (!conflicts.isEmpty()) {
            throw new SpaceConflictException(
                "Some selected spaces are not available.", ErrorCode.SPACE_CONFLICT, conflicts);
        }

        List<EventSpaceBooking> saved = bookingRepository.saveAll(bookings);

        log.info("Created {} stall bookings for event [{}] by user [{}]",
            saved.size(), eventId, userId);

        return saved.stream()
            .map(bookingMapper::toResponse)
            .toList();
    }

    private BookingLevel resolveBookingLevel(CreateEventSpaceBookingRequest request) {
        boolean hasVenue    = hasItems(request.venueIds());
        boolean hasBuilding = hasItems(request.buildingIds());
        boolean hasFloor    = hasItems(request.floorIds());
        boolean hasHall     = hasItems(request.hallIds());

        long levelCount = Stream.of(hasVenue, hasBuilding, hasFloor, hasHall)
            .filter(b -> b).count();

        if (levelCount == 0) {
            throw new BusinessException(
                "At least one space must be selected.",
                ErrorCode.VALIDATION_ERROR);
        }

        if (levelCount > 1) {
            throw new BusinessException(
                "Cannot mix booking levels in a single request. "
                + "Submit separate requests for each level.",
                ErrorCode.VALIDATION_ERROR);
        }

        if (hasVenue)    return BookingLevel.VENUE;
        if (hasBuilding) return BookingLevel.BUILDING;
        if (hasFloor)    return BookingLevel.FLOOR;
        return BookingLevel.HALL;
    }

    private void validateBookingLevelPermission(
            User requestingUser, 
            BookingLevel level,
            UUID eventId) {

        if (requestingUser.getSystemRole() == SystemRole.SUPER_ADMIN) return;

        List<OrganizationMember> memberships = memberRepository
            .findByUserIdWithOrganizations(requestingUser.getId());

        boolean isOrganizer = memberships.stream()
            .anyMatch(m -> m.getRole() == OrganizationRole.ORG_ADMIN
                && m.getOrganization().isEventOrganizer());

        boolean isVendor = memberships.stream()
            .anyMatch(m -> m.getRole() == OrganizationRole.ORG_ADMIN
                && m.getOrganization().isVendor());

        switch (level) {
            case VENUE, BUILDING, FLOOR -> {
                if (!isOrganizer) {
                    throw new ForbiddenException(
                        "Only event organizers can book at "
                        + level.name().toLowerCase() + " level.",
                        ErrorCode.FORBIDDEN);
                }
            }
            case HALL -> {
                if (!isOrganizer && !isVendor) {
                    throw new ForbiddenException(
                        "Only organizers or vendors can book halls.",
                        ErrorCode.FORBIDDEN);
                }
            }
            case STALL -> {
                if (!isVendor) {
                    throw new ForbiddenException(
                        "Only vendors can book individual stalls.",
                        ErrorCode.FORBIDDEN);
                }
                validateEventIsOpen(eventId);
            }
        }
    }

    private void validateEventIsOpen(UUID eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event not found", ErrorCode.EVENT_NOT_FOUND));
        if (event.getStartDateTime().isBefore(Instant.now())) {
            // Can be extended based on requirements
        }
    }

    private List<Stall> resolveStallsForBooking(EventSpaceBooking booking) {
        return switch (booking.getBookingLevel()) {
            case VENUE -> stallRepository.findByVenueIdAndActiveTrue(
                booking.getVenue().getId());
            case BUILDING -> stallRepository.findByBuildingIdAndActiveTrue(
                booking.getBuilding().getId());
            case FLOOR -> stallRepository.findByFloorIdAndActiveTrue(
                booking.getFloor().getId());
            case HALL -> stallRepository.findByHallIdAndActiveTrue(
                booking.getHall().getId());
            case STALL -> List.of(booking.getStall());
        };
    }

    private void processVenueBookings(UUID eventId, CreateEventSpaceBookingRequest request, Event event,
            List<EventSpaceBooking> bookings, List<ConflictDetail> conflicts) {
        for (UUID venueId : request.venueIds()) {
            Venue venue = venueRepository.findByIdAndActiveTrue(venueId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Venue not found: " + venueId, ErrorCode.VENUE_NOT_FOUND));

            List<EventSpaceBooking> existing = bookingRepository
                .findConflictsForVenue(
                    eventId, venueId,
                    request.startsAt(), request.endsAt());

            if (!existing.isEmpty()) {
                conflicts.add(new ConflictDetail(
                    BookingLevel.VENUE, venueId, venue.getName(),
                    existing.get(0)));
            } else {
                EventSpaceBooking b = new EventSpaceBooking();
                b.setEvent(event);
                b.setBookingLevel(BookingLevel.VENUE);
                b.setVenue(venue);
                b.setStatus(BookingStatus.PENDING);
                b.setPrice(pricingService.calculatePrice(BookingLevel.VENUE, venueId, request.startsAt(), request.endsAt()));
                b.setStartsAt(request.startsAt());
                b.setEndsAt(request.endsAt());
                bookings.add(b);
            }
        }
    }

    private void processBuildingBookings(UUID eventId, CreateEventSpaceBookingRequest request, Event event,
            List<EventSpaceBooking> bookings, List<ConflictDetail> conflicts) {
        for (UUID buildingId : request.buildingIds()) {
            Building building = buildingRepository.findByIdAndActiveTrue(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Building not found: " + buildingId, ErrorCode.BUILDING_NOT_FOUND));

            List<EventSpaceBooking> existing = bookingRepository
                .findConflictsForBuilding(
                    eventId, building.getVenue().getId(), buildingId,
                    request.startsAt(), request.endsAt());

            if (!existing.isEmpty()) {
                conflicts.add(new ConflictDetail(
                    BookingLevel.BUILDING, buildingId, building.getName(),
                    existing.get(0)));
            } else {
                EventSpaceBooking b = new EventSpaceBooking();
                b.setEvent(event);
                b.setBookingLevel(BookingLevel.BUILDING);
                b.setBuilding(building);
                b.setStatus(BookingStatus.PENDING);
                b.setPrice(pricingService.calculatePrice(BookingLevel.BUILDING, buildingId, request.startsAt(), request.endsAt()));
                b.setStartsAt(request.startsAt());
                b.setEndsAt(request.endsAt());
                bookings.add(b);
            }
        }
    }

    private void processFloorBookings(UUID eventId, CreateEventSpaceBookingRequest request, Event event,
            List<EventSpaceBooking> bookings, List<ConflictDetail> conflicts) {
        for (UUID floorId : request.floorIds()) {
            Floor floor = floorRepository.findByIdAndActiveTrue(floorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Floor not found: " + floorId, ErrorCode.FLOOR_NOT_FOUND));

            List<EventSpaceBooking> existing = bookingRepository
                .findConflictsForFloor(
                    eventId, floor.getBuilding().getVenue().getId(), floor.getBuilding().getId(), floorId,
                    request.startsAt(), request.endsAt());

            if (!existing.isEmpty()) {
                conflicts.add(new ConflictDetail(
                    BookingLevel.FLOOR, floorId, floor.getLevelName(),
                    existing.get(0)));
            } else {
                EventSpaceBooking b = new EventSpaceBooking();
                b.setEvent(event);
                b.setBookingLevel(BookingLevel.FLOOR);
                b.setFloor(floor);
                b.setStatus(BookingStatus.PENDING);
                b.setPrice(pricingService.calculatePrice(BookingLevel.FLOOR, floorId, request.startsAt(), request.endsAt()));
                b.setStartsAt(request.startsAt());
                b.setEndsAt(request.endsAt());
                bookings.add(b);
            }
        }
    }

    private void processHallBookings(UUID eventId, CreateEventSpaceBookingRequest request, Event event,
            List<EventSpaceBooking> bookings, List<ConflictDetail> conflicts) {
        for (UUID hallId : request.hallIds()) {
            Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Hall not found: " + hallId, ErrorCode.HALL_NOT_FOUND));

            List<EventSpaceBooking> existing = bookingRepository
                .findConflictsForHall(
                    eventId, hall.getFloor().getBuilding().getVenue().getId(), hall.getFloor().getBuilding().getId(), hall.getFloor().getId(), hallId,
                    request.startsAt(), request.endsAt());

            if (!existing.isEmpty()) {
                conflicts.add(new ConflictDetail(
                    BookingLevel.HALL, hallId, hall.getName(),
                    existing.get(0)));
            } else {
                EventSpaceBooking b = new EventSpaceBooking();
                b.setEvent(event);
                b.setBookingLevel(BookingLevel.HALL);
                b.setHall(hall);
                b.setStatus(BookingStatus.PENDING);
                b.setPrice(pricingService.calculatePrice(BookingLevel.HALL, hallId, request.startsAt(), request.endsAt()));
                b.setStartsAt(request.startsAt());
                b.setEndsAt(request.endsAt());
                bookings.add(b);
            }
        }
    }

    private void validateDateRange(Instant start, Instant end) {
        if (!start.isBefore(end)) {
            throw new BusinessException(
                "Start must be before end.", ErrorCode.VALIDATION_ERROR);
        }
        if (start.isBefore(Instant.now())) {
            throw new BusinessException(
                "Cannot book in the past.", ErrorCode.VALIDATION_ERROR);
        }
    }

    private boolean hasItems(Set<UUID> set) {
        return set != null && !set.isEmpty();
    }

    @Transactional(readOnly = true)
    public List<EventSpaceBookingResponse> getBookingsForEvent(UUID eventId, org.springframework.data.domain.Pageable pageable) {
        return bookingRepository.findByEventId(eventId, pageable).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Transactional
    public void cancelBooking(UUID userId, UUID eventId, UUID bookingId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.USER_NOT_FOUND));

        EventSpaceBooking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found", ErrorCode.BOOKING_NOT_FOUND));

        if (!booking.getEvent().getId().equals(eventId)) {
            throw new BusinessException("Booking does not belong to this event", ErrorCode.VALIDATION_ERROR);
        }

        validateCancelPermission(user, booking);

        if (booking.getBookingLevel() != BookingLevel.STALL) {
            // Organizer cancels space booking -> check no active vendor reservations first
            List<Stall> physicalStalls = resolveStallsForBooking(booking);
            List<UUID> physicalStallIds = physicalStalls.stream().map(Stall::getId).toList();

            if (!physicalStallIds.isEmpty()) {
                List<Reservation> activeReservations = reservationRepository.findByEventIdAndStallIdInAndStatusIn(
                    eventId, physicalStallIds, List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED));

                if (!activeReservations.isEmpty()) {
                    throw new BusinessException("Cannot cancel space booking while vendors have active stall reservations", ErrorCode.BUSINESS_RULE_VIOLATION);
                }

                List<EventStall> eventStalls = eventStallRepository.findByEventIdAndStallIdIn(eventId, physicalStallIds);
                eventStalls.forEach(es -> es.setActiveForEvent(false));
                eventStallRepository.saveAll(eventStalls);
            }
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);
        } else {
            // Vendor cancels stall booking -> release EventStall availability status
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);
        }
    }

    // SUPER_ADMIN can cancel any booking. Otherwise: STALL bookings may only be cancelled by
    // the vendor who made the linked reservation (or an ORG_ADMIN of that vendor's org); all
    // other levels may only be cancelled by an ORG_ADMIN of the event's organizing org.
    private void validateCancelPermission(User user, EventSpaceBooking booking) {
        if (user.getSystemRole() == SystemRole.SUPER_ADMIN) {
            return;
        }

        if (booking.getBookingLevel() == BookingLevel.STALL) {
            Reservation reservation = booking.getReservation();
            if (reservation != null && reservation.getUser().getId().equals(user.getId())) {
                return;
            }
            if (reservation != null && memberRepository
                    .findByUserIdAndOrganizationId(user.getId(), reservation.getOrganization().getId())
                    .map(m -> m.getRole() == OrganizationRole.ORG_ADMIN)
                    .orElse(false)) {
                return;
            }
            throw new ForbiddenException("You cannot cancel this stall booking.", ErrorCode.FORBIDDEN);
        }

        boolean isOrganizerAdmin = memberRepository
            .findByUserIdAndOrganizationId(user.getId(), booking.getEvent().getOrganizer().getId())
            .map(m -> m.getRole() == OrganizationRole.ORG_ADMIN)
            .orElse(false);

        if (!isOrganizerAdmin) {
            throw new ForbiddenException("You cannot cancel this booking.", ErrorCode.FORBIDDEN);
        }
    }
}
