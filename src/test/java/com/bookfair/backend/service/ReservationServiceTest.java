package com.bookfair.backend.service;

import com.bookfair.backend.dto.event.request.CreateStallBookingRequest;
import com.bookfair.backend.dto.event.response.EventSpaceBookingResponse;
import com.bookfair.backend.dto.reservation.mapper.ReservationMapper;
import com.bookfair.backend.dto.reservation.request.CreateReservationRequest;
import com.bookfair.backend.dto.reservation.response.ReservationResponse;
import com.bookfair.backend.exception.BookingExpiredException;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ForbiddenException;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.*;
import com.bookfair.backend.model.enums.BookingStatus;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.model.enums.ReservationStatus;
import com.bookfair.backend.model.enums.SystemRole;
import com.bookfair.backend.repository.*;
import com.bookfair.backend.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private EventSpaceBookingRepository bookingRepository;
    @Mock private EventSpaceBookingService bookingService;
    @Mock private EventRepository eventRepository;
    @Mock private QRService qrCodeService;
    @Mock private GenreRepository genreRepository;
    @Mock private ReservationMapper reservationMapper;
    @Mock private ReservationAuthorizationService authorizationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks
    private ReservationService reservationService;

    private UUID userId;
    private UUID reservationId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setUsername("vendor1");
        user.setEmail("vendor1@example.com");
        user.setSystemRole(SystemRole.CUSTOMER);
    }

    private Reservation reservationWith(ReservationStatus status, Instant expiresAt) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setName("Spring Book Fair");

        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setStatus(status);
        reservation.setExpiresAt(expiresAt);
        reservation.setUser(user);
        reservation.setEvent(event);
        reservation.setSpaceBookings(new java.util.ArrayList<>());
        return reservation;
    }

    // ---------- createReservation ----------

    @Test
    void createReservation_rejectsWhenUserNotOrgMember() {
        CreateReservationRequest request = new CreateReservationRequest(
                UUID.randomUUID(), // eventId
                null, null, null, null, // venueIds/buildingIds/floorIds/hallIds
                java.util.Set.of(UUID.randomUUID()), // stallIds
                Instant.now(), // reservationStartDateTime
                Instant.now().plus(15, ChronoUnit.MINUTES), // expiresAt
                UUID.randomUUID(), // genreId
                UUID.randomUUID()); // organizationId

        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findByIdAndActiveTrue(userId)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndActiveTrue(any())).thenReturn(Optional.of(new Event()));
        when(genreRepository.findByIdAndActiveTrue(any())).thenReturn(Optional.of(new Genre()));
        Organization org = new Organization();
        org.setId(request.organizationId());
        when(organizationRepository.findById(request.organizationId())).thenReturn(Optional.of(org));
        when(memberRepository.existsByUserIdAndOrganizationId(userId, org.getId())).thenReturn(false);

        assertThatThrownBy(() -> reservationService.createReservation(request))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(bookingService);
    }

    // ---------- confirmReservation ----------

    @Test
    void confirmReservation_confirmsWhenPendingAndNotExpired() {
        Reservation reservation = reservationWith(ReservationStatus.PENDING, Instant.now().plus(5, ChronoUnit.MINUTES));
        EventSpaceBooking booking = new EventSpaceBooking();
        booking.setStatus(BookingStatus.PENDING);
        reservation.getSpaceBookings().add(booking);

        when(reservationRepository.findByIdAndStatusForUpdate(reservationId, ReservationStatus.PENDING))
                .thenReturn(Optional.of(reservation));
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authorizationService.canConfirmReservation(user, reservation)).thenReturn(true);
        when(qrCodeService.generateQRCode(any())).thenReturn("base64");

        reservationService.confirmReservation(reservationId);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(reservationRepository).save(reservation);
    }

    @Test
    void confirmReservation_throwsForbidden_whenNotAuthorized() {
        Reservation reservation = reservationWith(ReservationStatus.PENDING, Instant.now().plus(5, ChronoUnit.MINUTES));
        when(reservationRepository.findByIdAndStatusForUpdate(reservationId, ReservationStatus.PENDING))
                .thenReturn(Optional.of(reservation));
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authorizationService.canConfirmReservation(user, reservation)).thenReturn(false);

        assertThatThrownBy(() -> reservationService.confirmReservation(reservationId))
                .isInstanceOf(ForbiddenException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void confirmReservation_throwsBookingExpired_whenHoldExpired() {
        Reservation reservation = reservationWith(ReservationStatus.PENDING, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(reservationRepository.findByIdAndStatusForUpdate(reservationId, ReservationStatus.PENDING))
                .thenReturn(Optional.of(reservation));
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authorizationService.canConfirmReservation(user, reservation)).thenReturn(true);

        assertThatThrownBy(() -> reservationService.confirmReservation(reservationId))
                .isInstanceOf(BookingExpiredException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void confirmReservation_throwsNotFound_whenNotPending() {
        when(reservationRepository.findByIdAndStatusForUpdate(reservationId, ReservationStatus.PENDING))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.confirmReservation(reservationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- requestCancellation ----------

    @Test
    void requestCancellation_cancelsDirectly_whenPending() {
        Reservation reservation = reservationWith(ReservationStatus.PENDING, Instant.now().plus(5, ChronoUnit.MINUTES));
        EventSpaceBooking booking = new EventSpaceBooking();
        booking.setStatus(BookingStatus.PENDING);
        reservation.getSpaceBookings().add(booking);

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authorizationService.canManageReservation(user, reservation)).thenReturn(true);

        reservationService.requestCancellation(reservationId);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(eventPublisher, never()).publishEvent(any(com.bookfair.backend.event.reservation.ReservationRefundPendingEvent.class));
    }

    @Test
    void requestCancellation_movesToRefundPending_whenConfirmed() {
        Reservation reservation = reservationWith(ReservationStatus.CONFIRMED, Instant.now().plus(5, ChronoUnit.MINUTES));

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authorizationService.canManageReservation(user, reservation)).thenReturn(true);

        reservationService.requestCancellation(reservationId);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REFUND_PENDING);
        verify(eventPublisher).publishEvent(any(com.bookfair.backend.event.reservation.ReservationRefundPendingEvent.class));
    }

    @Test
    void requestCancellation_rejectsWhenAlreadyRefunded() {
        Reservation reservation = reservationWith(ReservationStatus.REFUNDED, Instant.now().plus(5, ChronoUnit.MINUTES));

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authorizationService.canManageReservation(user, reservation)).thenReturn(true);

        assertThatThrownBy(() -> reservationService.requestCancellation(reservationId))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- approveRefund ----------

    @Test
    void approveRefund_refundsWhenAuthorized() {
        Reservation reservation = reservationWith(ReservationStatus.REFUND_PENDING, Instant.now().plus(5, ChronoUnit.MINUTES));
        EventSpaceBooking booking = new EventSpaceBooking();
        reservation.getSpaceBookings().add(booking);

        when(reservationRepository.findByIdAndStatusForUpdate(reservationId, ReservationStatus.REFUND_PENDING))
                .thenReturn(Optional.of(reservation));
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authorizationService.canApproveRefund(user, reservation)).thenReturn(true);

        reservationService.approveRefund(reservationId);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REFUNDED);
        verify(reservationRepository).save(reservation);
    }

    // ---------- getAllReservations ----------

    @Test
    void getAllReservations_superAdminSeesEverything() {
        user.setSystemRole(SystemRole.SUPER_ADMIN);
        Pageable pageable = PageRequest.of(0, 20);
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reservationRepository.findAll(pageable)).thenReturn(Page.empty());

        reservationService.getAllReservations(pageable);

        verify(reservationRepository).findAll(pageable);
        verify(reservationRepository, never()).findByOrganizationId(any(), any());
    }

    @Test
    void getAllReservations_orgAdminScopedToOwnOrg() {
        Pageable pageable = PageRequest.of(0, 20);
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        OrganizationMember membership = new OrganizationMember();
        membership.setRole(OrganizationRole.ORG_ADMIN);
        membership.setOrganization(organization);

        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(membership));
        when(reservationRepository.findByOrganizationId(organization.getId(), pageable))
                .thenReturn(Page.empty());

        reservationService.getAllReservations(pageable);

        verify(reservationRepository).findByOrganizationId(organization.getId(), pageable);
        verify(reservationRepository, never()).findAll(pageable);
    }

    @Test
    void getAllReservations_forbidsPlainMember() {
        Pageable pageable = PageRequest.of(0, 20);
        OrganizationMember membership = new OrganizationMember();
        membership.setRole(OrganizationRole.ORG_MEMBER);

        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> reservationService.getAllReservations(pageable))
                .isInstanceOf(ForbiddenException.class);
    }
}
