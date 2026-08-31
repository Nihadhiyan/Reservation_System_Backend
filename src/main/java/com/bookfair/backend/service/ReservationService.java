package com.bookfair.backend.service;

import com.bookfair.backend.dto.reservation.mapper.ReservationMapper;
import com.bookfair.backend.dto.reservation.request.CreateReservationRequest;
import com.bookfair.backend.dto.reservation.response.ReservationDetailResponse;
import com.bookfair.backend.dto.reservation.response.ReservationResponse;
import com.bookfair.backend.exception.BookingExpiredException;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ForbiddenException;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.exception.StallUnavailableException;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.ReservationStatus;
import com.bookfair.backend.model.EventSpaceBooking;
import com.bookfair.backend.model.enums.BookingStatus;
import com.bookfair.backend.model.Genre;
import com.bookfair.backend.event.reservation.ReservationConfirmedEvent;
import com.bookfair.backend.event.reservation.ReservationRefundPendingEvent;
import com.bookfair.backend.event.reservation.ReservationRefundedEvent;
import com.bookfair.backend.event.reservation.ReservationRequestReceivedEvent;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.repository.EventSpaceBookingRepository;
import com.bookfair.backend.repository.GenreRepository;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.repository.UserRepository;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import com.bookfair.backend.repository.OrganizationRepository;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.OrganizationMember;
import com.bookfair.backend.model.User;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.dto.event.request.CreateStallBookingRequest;
import com.bookfair.backend.dto.event.response.EventSpaceBookingResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bookfair.backend.util.SecurityUtils;
import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

        private final ReservationRepository reservationRepository;
        private final UserRepository userRepository;
        private final OrganizationMemberRepository memberRepository;
        private final OrganizationRepository organizationRepository;
        private final EventSpaceBookingRepository bookingRepository;
        private final EventSpaceBookingService bookingService;
        private final EventRepository eventRepository;
        private final QRService qrCodeService;
        private final GenreRepository genreRepository;
        private final ReservationMapper reservationMapper;
        private final ReservationAuthorizationService authorizationService;
        private final ApplicationEventPublisher eventPublisher;
        private final SecurityUtils securityUtils;

        @Transactional(readOnly = true)
        public List<ReservationResponse> getMyReservations(String username) {
                User user = userRepository.findByUsernameAndActiveTrue(requireNonNull(username))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found",
                                                ErrorCode.USER_NOT_FOUND));

                return reservationRepository.findByUserId(requireNonNull(user.getId())).stream()
                                .map(reservationMapper::toReservationResponse)
                                .toList();
        }

        @Transactional
        public ReservationResponse createReservation(CreateReservationRequest request) {
                requireNonNull(request, "request cannot be null");
                User user = userRepository.findByIdAndActiveTrue(requireNonNull(securityUtils.getCurrentUserId()))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found",
                                                ErrorCode.USER_NOT_FOUND));

                Event event = eventRepository.findByIdAndActiveTrue(requireNonNull(request.eventId()))
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found",
                                                ErrorCode.EVENT_NOT_FOUND));

                Genre genre = genreRepository.findByIdAndActiveTrue(requireNonNull(request.genreId()))
                                .orElseThrow(() -> new ResourceNotFoundException("Genre not found",
                                                ErrorCode.GENRE_NOT_FOUND));

                Organization organization = organizationRepository.findById(requireNonNull(request.organizationId()))
                                .orElseThrow(() -> new ResourceNotFoundException("Organization not found",
                                                ErrorCode.ORGANIZATION_NOT_FOUND));

                if (!memberRepository.existsByUserIdAndOrganizationId(requireNonNull(user.getId()),
                                requireNonNull(organization.getId()))) {
                        throw new BusinessException(
                                        "User must belong to the organization to make a reservation on its behalf.",
                                        ErrorCode.BUSINESS_RULE_VIOLATION);
                }

                Instant startDateTime = request.reservationStartDateTime() != null
                                ? request.reservationStartDateTime()
                                : event.getStartDateTime();
                Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
                
                CreateStallBookingRequest bookingRequest = new CreateStallBookingRequest(
                        request.stallIds(), startDateTime, expiresAt, null
                );
                
                List<EventSpaceBookingResponse> createdBookings = bookingService.createStallBookings(user.getId(), event.getId(), bookingRequest);
                
                List<EventSpaceBooking> bookings = bookingRepository.findAllById(createdBookings.stream().map(EventSpaceBookingResponse::id).toList());

                Reservation reservation = reservationMapper.toReservation(
                                user, organization, event, genre, startDateTime, expiresAt);

                BigDecimal totalPrice = bookings.stream()
                                .map(EventSpaceBooking::getPrice)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                reservation.setTotalPrice(totalPrice);
                
                // link the bookings to the reservation
                for (EventSpaceBooking b : bookings) {
                    b.setReservation(reservation);
                }
                reservation.setSpaceBookings(bookings);

                Reservation savedReservation = reservationRepository.save(reservation);

                eventPublisher.publishEvent(new ReservationRequestReceivedEvent(requireNonNull(user.getId()),
                                requireNonNull(user.getUsername()),
                                requireNonNull(user.getEmail()), requireNonNull(savedReservation.getId()),
                                requireNonNull(event.getName())));

                return reservationMapper.toReservationResponse(savedReservation);
        }

        @Transactional
        public void confirmReservation(UUID reservationId) {
                Reservation reservation = reservationRepository
                                .findByIdAndStatusForUpdate(requireNonNull(reservationId), ReservationStatus.PENDING)
                                .orElseThrow(
                                                () -> new ResourceNotFoundException("Reservation not found",
                                                                ErrorCode.RESERVATION_NOT_FOUND));

                User requestingUser = userRepository.findById(requireNonNull(securityUtils.getCurrentUserId()))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found",
                                                ErrorCode.USER_NOT_FOUND));

                if (!authorizationService.canConfirmReservation(requestingUser, reservation)) {
                        throw new ForbiddenException("You cannot manage reservations for this organization.",
                                        ErrorCode.FORBIDDEN);
                }

                if (reservation.getExpiresAt().isBefore(Instant.now())) {
                        throw new BookingExpiredException("Your reservation timer has expired. Please start over.",
                                        ErrorCode.BOOKING_EXPIRED);
                }

                reservation.setStatus(ReservationStatus.CONFIRMED);

                String qrPayload = "RES-" + reservation.getId();
                reservation.setQrCodePayload(qrPayload);
                String qrCodeImage = qrCodeService.generateQRCode(qrPayload);

                for (EventSpaceBooking b : reservation.getSpaceBookings()) {
                        b.setStatus(BookingStatus.CONFIRMED);
                        bookingRepository.save(b);
                }

                reservationRepository.save(reservation);

                eventPublisher.publishEvent(new ReservationConfirmedEvent(requireNonNull(reservation.getUser().getId()),
                                requireNonNull(reservation.getUser().getUsername()),
                                requireNonNull(reservation.getUser().getEmail()),
                                requireNonNull(reservation.getId()),
                                requireNonNull(reservation.getEvent().getName()), requireNonNull(qrCodeImage)));
        }

        @Transactional
        public void requestCancellation(UUID reservationId) {
                Reservation reservation = reservationRepository.findByIdForUpdate(requireNonNull(reservationId))
                                .orElseThrow(
                                                () -> new ResourceNotFoundException("Reservation not found",
                                                                ErrorCode.RESERVATION_NOT_FOUND));

                User requestingUser = userRepository.findById(requireNonNull(securityUtils.getCurrentUserId()))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found",
                                                ErrorCode.USER_NOT_FOUND));

                if (!authorizationService.canManageReservation(requestingUser, reservation)) {
                        throw new ForbiddenException("You cannot cancel this reservation.", ErrorCode.FORBIDDEN);
                }

                if (reservation.getStatus() == ReservationStatus.PENDING) {
                        reservation.setStatus(ReservationStatus.CANCELLED);
                        for (EventSpaceBooking b : reservation.getSpaceBookings()) {
                                b.setStatus(BookingStatus.CANCELLED);
                                bookingRepository.save(b);
                        }
                        reservationRepository.save(reservation);
                        return;
                }

                if (!reservation.getStatus().equals(ReservationStatus.CONFIRMED)) {
                        throw new BusinessException(
                                        "Only confirmed or pending reservations can be cancelled.",
                                        ErrorCode.BUSINESS_RULE_VIOLATION);
                }

                reservation.setStatus(ReservationStatus.REFUND_PENDING);
                reservationRepository.save(reservation);

                eventPublisher.publishEvent(
                                new ReservationRefundPendingEvent(requireNonNull(reservation.getUser().getId()),
                                                requireNonNull(reservation.getUser().getUsername()),
                                                requireNonNull(reservation.getUser().getEmail()),
                                                requireNonNull(reservation.getId()),
                                                requireNonNull(reservation.getEvent().getName())));
        }

        @Transactional
        public void approveRefund(UUID reservationId) {
                Reservation reservation = reservationRepository
                                .findByIdAndStatusForUpdate(requireNonNull(reservationId), ReservationStatus.REFUND_PENDING)
                                .orElseThrow(
                                                () -> new ResourceNotFoundException("Reservation not found",
                                                                ErrorCode.RESERVATION_NOT_FOUND));

                User requestingUser = userRepository.findById(requireNonNull(securityUtils.getCurrentUserId()))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found",
                                                ErrorCode.USER_NOT_FOUND));

                if (!authorizationService.canApproveRefund(requestingUser, reservation)) {
                        throw new ForbiddenException("You cannot approve refunds for this reservation.",
                                        ErrorCode.FORBIDDEN);
                }

                reservation.setStatus(ReservationStatus.REFUNDED);

                for (EventSpaceBooking b : reservation.getSpaceBookings()) {
                        b.setStatus(BookingStatus.CANCELLED); // Or REFUNDED, depending on enum
                        bookingRepository.save(b);
                }

                reservationRepository.save(reservation);

                eventPublisher.publishEvent(
                                new ReservationRefundedEvent(requireNonNull(reservation.getUser().getId()),
                                                requireNonNull(reservation.getUser().getUsername()),
                                                requireNonNull(reservation.getUser().getEmail()),
                                                requireNonNull(reservation.getId()),
                                                requireNonNull(reservation.getEvent().getName())));
        }

        @Transactional(readOnly = true)
        public Page<ReservationResponse> getAllReservations(Pageable pageable) {
                User requestingUser = userRepository.findById(requireNonNull(securityUtils.getCurrentUserId()))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found",
                                                ErrorCode.USER_NOT_FOUND));

                if (requestingUser.getSystemRole() == com.bookfair.backend.model.enums.SystemRole.SUPER_ADMIN) {
                        return reservationRepository.findAll(requireNonNull(pageable))
                                        .map(reservationMapper::toReservationResponse);
                }

                OrganizationMember membership = memberRepository.findByUserId(requestingUser.getId()).stream()
                                .filter(m -> m.getRole() == com.bookfair.backend.model.enums.OrganizationRole.ORG_ADMIN)
                                .findFirst()
                                .orElseThrow(() -> new ForbiddenException(
                                                "You must be an organization admin to view reservations.",
                                                ErrorCode.FORBIDDEN));

                return reservationRepository
                                .findByOrganizationId(requireNonNull(membership.getOrganization().getId()), requireNonNull(pageable))
                                .map(reservationMapper::toReservationResponse);
        }

        @Transactional(readOnly = true)
        public ReservationResponse getReservationById(UUID id) {
                Reservation reservation = reservationRepository.findById(requireNonNull(id))
                                .orElseThrow(
                                                () -> new ResourceNotFoundException("Reservation not found",
                                                                ErrorCode.RESERVATION_NOT_FOUND));

                checkReadAccess(reservation);

                return reservationMapper.toReservationResponse(reservation);
        }

        @Transactional(readOnly = true)
        public ReservationDetailResponse getReservationDetails(UUID id) {
                Reservation reservation = reservationRepository.findById(requireNonNull(id))
                                .orElseThrow(
                                                () -> new ResourceNotFoundException("Reservation not found",
                                                                ErrorCode.RESERVATION_NOT_FOUND));

                checkReadAccess(reservation);

                return reservationMapper.toReservationDetailResponse(reservation);
        }

        private void checkReadAccess(Reservation reservation) {
                User requestingUser = userRepository.findById(requireNonNull(securityUtils.getCurrentUserId()))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found",
                                                ErrorCode.USER_NOT_FOUND));

                if (!authorizationService.canViewReservation(requestingUser, reservation)) {
                        throw new ForbiddenException("You do not have permission to view this reservation.",
                                        ErrorCode.FORBIDDEN);
                }
        }
}
