package com.bookfair.backend.service;

import com.bookfair.backend.dto.event.mapper.EventSpaceBookingMapper;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ForbiddenException;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.*;
import com.bookfair.backend.model.enums.BookingLevel;
import com.bookfair.backend.model.enums.BookingStatus;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.model.enums.SystemRole;
import com.bookfair.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the cancelBooking authorization fix: previously ANY authenticated
 * user (any org, even unrelated to the event) could cancel any booking simply
 * by knowing its ID — validateCancelPermission closes that gap.
 */
@ExtendWith(MockitoExtension.class)
class EventSpaceBookingServiceTest {

    @Mock private EventSpaceBookingRepository bookingRepository;
    @Mock private EventRepository eventRepository;
    @Mock private VenueRepository venueRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private FloorRepository floorRepository;
    @Mock private HallRepository hallRepository;
    @Mock private StallRepository stallRepository;
    @Mock private PricingService pricingService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EventSpaceBookingMapper bookingMapper;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private EventStallService eventStallService;
    @Mock private ReservationRepository reservationRepository;
    @Mock private EventStallRepository eventStallRepository;

    @InjectMocks
    private EventSpaceBookingService bookingService;

    private UUID userId;
    private UUID eventId;
    private UUID bookingId;
    private User user;
    private Organization organizerOrg;
    private Event event;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setSystemRole(SystemRole.CUSTOMER);

        organizerOrg = new Organization();
        organizerOrg.setId(UUID.randomUUID());

        event = new Event();
        event.setId(eventId);
        event.setOrganizer(organizerOrg);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private EventSpaceBooking hallBooking() {
        Hall hall = new Hall();
        hall.setId(UUID.randomUUID());
        Floor floor = new Floor();
        Building building = new Building();
        Venue venue = new Venue();
        building.setVenue(venue);
        floor.setBuilding(building);
        hall.setFloor(floor);

        EventSpaceBooking booking = new EventSpaceBooking();
        booking.setId(bookingId);
        booking.setEvent(event);
        booking.setBookingLevel(BookingLevel.HALL);
        booking.setHall(hall);
        booking.setStatus(BookingStatus.CONFIRMED);
        return booking;
    }

    private EventSpaceBooking stallBookingFor(Reservation reservation) {
        Stall stall = new Stall();
        stall.setId(UUID.randomUUID());
        Hall hall = new Hall();
        Floor floor = new Floor();
        Building building = new Building();
        Venue venue = new Venue();
        building.setVenue(venue);
        floor.setBuilding(building);
        hall.setFloor(floor);
        stall.setHall(hall);

        EventSpaceBooking booking = new EventSpaceBooking();
        booking.setId(bookingId);
        booking.setEvent(event);
        booking.setBookingLevel(BookingLevel.STALL);
        booking.setStall(stall);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setReservation(reservation);
        return booking;
    }

    @Test
    void superAdmin_canCancelAnyBooking() {
        user.setSystemRole(SystemRole.SUPER_ADMIN);
        EventSpaceBooking booking = hallBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(stallRepository.findByHallIdAndActiveTrue(any())).thenReturn(List.of());

        bookingService.cancelBooking(userId, eventId, bookingId);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void orgAdminOfOrganizer_canCancelHallBooking() {
        EventSpaceBooking booking = hallBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(stallRepository.findByHallIdAndActiveTrue(any())).thenReturn(List.of());

        OrganizationMember membership = new OrganizationMember();
        membership.setRole(OrganizationRole.ORG_ADMIN);
        when(memberRepository.findByUserIdAndOrganizationId(userId, organizerOrg.getId()))
                .thenReturn(Optional.of(membership));

        bookingService.cancelBooking(userId, eventId, bookingId);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).save(booking);
    }

    @Test
    void unrelatedUser_cannotCancelHallBooking() {
        // This is the exact gap the fix closes: an authenticated user with no
        // relationship to the event's organizing org must be refused.
        EventSpaceBooking booking = hallBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(memberRepository.findByUserIdAndOrganizationId(userId, organizerOrg.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(userId, eventId, bookingId))
                .isInstanceOf(ForbiddenException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void plainOrgMember_cannotCancelHallBooking() {
        // ORG_MEMBER (not ORG_ADMIN) of the organizing org must also be refused.
        EventSpaceBooking booking = hallBooking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        OrganizationMember membership = new OrganizationMember();
        membership.setRole(OrganizationRole.ORG_MEMBER);
        when(memberRepository.findByUserIdAndOrganizationId(userId, organizerOrg.getId()))
                .thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> bookingService.cancelBooking(userId, eventId, bookingId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void reservationOwner_canCancelTheirOwnStallBooking() {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        Organization vendorOrg = new Organization();
        vendorOrg.setId(UUID.randomUUID());
        reservation.setOrganization(vendorOrg);

        EventSpaceBooking booking = stallBookingFor(reservation);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        bookingService.cancelBooking(userId, eventId, bookingId);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void unrelatedVendor_cannotCancelSomeoneElsesStallBooking() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        Reservation reservation = new Reservation();
        reservation.setUser(otherUser);
        Organization vendorOrg = new Organization();
        vendorOrg.setId(UUID.randomUUID());
        reservation.setOrganization(vendorOrg);

        EventSpaceBooking booking = stallBookingFor(reservation);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(memberRepository.findByUserIdAndOrganizationId(userId, vendorOrg.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(userId, eventId, bookingId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsCancel_whenBookingBelongsToDifferentEvent() {
        EventSpaceBooking booking = hallBooking();
        UUID differentEventId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(userId, differentEventId, bookingId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsCancel_whenActiveVendorReservationsExistOnTheStalls() {
        user.setSystemRole(SystemRole.SUPER_ADMIN);
        EventSpaceBooking booking = hallBooking();
        Stall stall = new Stall();
        stall.setId(UUID.randomUUID());
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(stallRepository.findByHallIdAndActiveTrue(any())).thenReturn(List.of(stall));
        when(reservationRepository.findByEventIdAndStallIdInAndStatusIn(any(), any(), any()))
                .thenReturn(List.of(new Reservation()));

        assertThatThrownBy(() -> bookingService.cancelBooking(userId, eventId, bookingId))
                .isInstanceOf(BusinessException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void throwsNotFound_whenBookingDoesNotExist() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(userId, eventId, bookingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
