package com.bookfair.backend.listener;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


import com.bookfair.backend.event.hierarchy.BuildingDeactivatedEvent;
import com.bookfair.backend.event.hierarchy.EventDeactivatedEvent;
import com.bookfair.backend.event.hierarchy.FloorDeactivatedEvent;
import com.bookfair.backend.event.hierarchy.HallDeactivatedEvent;
import com.bookfair.backend.event.hierarchy.VenueDeactivatedEvent;
import com.bookfair.backend.event.reservation.ReservationCancelledByAdminEvent;
import com.bookfair.backend.event.stall.StallDeactivatedEvent;
import com.bookfair.backend.model.Building;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.EventSpaceBooking;
import com.bookfair.backend.model.enums.BookingStatus;
import com.bookfair.backend.model.Floor;
import com.bookfair.backend.model.Hall;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.ReservationStatus;
import com.bookfair.backend.model.Stall;
import com.bookfair.backend.model.enums.BookingStatus;
import com.bookfair.backend.repository.BuildingRepository;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.repository.EventSpaceBookingRepository;
import com.bookfair.backend.repository.FloorRepository;
import com.bookfair.backend.repository.HallRepository;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.repository.StallRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class HierarchyDeactivationListener {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final HallRepository hallRepository;
    private final StallRepository stallRepository;
    private final EventRepository eventRepository;
    private final EventSpaceBookingRepository bookingRepository;
    private final ReservationRepository reservationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onVenueDeactivated(VenueDeactivatedEvent event) {
        log.info("Processing cascade deactivation for venue: {}", event.venueId());
        List<Building> buildings = buildingRepository.findByVenueIdAndActiveTrue(event.venueId());
        if (!buildings.isEmpty()) {
            buildings.forEach(building -> building.setActive(false));
            buildingRepository.saveAll(buildings);
            log.info("Deactivated {} buildings for venue {}", buildings.size(), event.venueId());
            buildings.forEach(building -> eventPublisher.publishEvent(new BuildingDeactivatedEvent(building.getId())));
        }
        List<Event> events = eventRepository.findByVenueIdAndActiveTrue(event.venueId());
        if (!events.isEmpty()) {
            events.forEach(e -> {
                e.setActive(false);
                eventPublisher.publishEvent(new EventDeactivatedEvent(e.getId()));
            });
            eventRepository.saveAll(events);
            log.info("Deactivated {} events for venue {}", events.size(), event.venueId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBuildingDeactivated(BuildingDeactivatedEvent event) {
        log.info("Processing cascade deactivation for building: {}", event.buildingId());
        List<Floor> floors = floorRepository.findByBuildingIdAndActiveTrue(event.buildingId());
        if (!floors.isEmpty()) {
            floors.forEach(floor -> floor.setActive(false));
            floorRepository.saveAll(floors);
            log.info("Deactivated {} floors for building {}", floors.size(), event.buildingId());
            floors.forEach(floor -> eventPublisher.publishEvent(new FloorDeactivatedEvent(floor.getId())));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onFloorDeactivated(FloorDeactivatedEvent event) {
        log.info("Processing cascade deactivation for floor: {}", event.floorId());
        List<Hall> halls = hallRepository.findByFloorIdAndActiveTrue(event.floorId());
        if (!halls.isEmpty()) {
            halls.forEach(hall -> hall.setActive(false));
            hallRepository.saveAll(halls);
            log.info("Deactivated {} halls for floor {}", halls.size(), event.floorId());
            halls.forEach(hall -> eventPublisher.publishEvent(new HallDeactivatedEvent(hall.getId())));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onHallDeactivated(HallDeactivatedEvent event) {
        log.info("Processing cascade deactivation for hall: {}", event.hallId());
        List<Stall> stalls = stallRepository.findByHallIdAndActiveTrue(event.hallId());
        if (!stalls.isEmpty()) {
            stalls.forEach(stall -> {
                stall.setActive(false);
                eventPublisher.publishEvent(new StallDeactivatedEvent(stall.getId()));
            });
            stallRepository.saveAll(stalls);
            log.info("Deactivated {} stalls for hall {}", stalls.size(), event.hallId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onStallDeactivated(StallDeactivatedEvent event) {
        log.info("Processing cascade deactivation for stall: {}", event.stallId());
        // With EventSpaceBooking, stall deactivation doesn't need to mutate historical bookings.
        // Active bookings are already prevented from existing by the service layer validations.
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onEventDeactivated(EventDeactivatedEvent event) {
        log.info("Processing cascade deactivation for event: {}", event.eventId());
        List<EventSpaceBooking> bookings = bookingRepository.findByEventIdAndStatusIn(
                event.eventId(), List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));
                
        if (!bookings.isEmpty()) {
            bookings.forEach(b -> {
                b.setStatus(BookingStatus.CANCELLED);
                deactivateReservationsForBooking(b);
            });
            bookingRepository.saveAll(bookings);
            log.info("Deactivated {} space bookings for event {}", bookings.size(), event.eventId());
        }
    }

    private void deactivateReservationsForBooking(EventSpaceBooking b) {
        if (b.getReservation() != null && 
            (b.getReservation().getStatus() == ReservationStatus.PENDING || b.getReservation().getStatus() == ReservationStatus.CONFIRMED)) {
            
            Reservation r = b.getReservation();
            r.setStatus(ReservationStatus.CANCELLED);
            String username = r.getUser() != null ? r.getUser().getUsername() : "User";
            String email = r.getUser() != null ? r.getUser().getEmail() : null;
            String eventName = r.getEvent() != null ? r.getEvent().getName() : "Event";
            if (email != null) {
                eventPublisher.publishEvent(new ReservationCancelledByAdminEvent(r.getId(), username, email, eventName, "Administrative closure of event"));
            }
            reservationRepository.save(r);
        }
    }
}
