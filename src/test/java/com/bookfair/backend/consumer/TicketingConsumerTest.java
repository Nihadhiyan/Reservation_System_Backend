package com.bookfair.backend.consumer;

import com.bookfair.backend.event.payment.PaymentCompletedEvent;
import com.bookfair.backend.model.EventSpaceBooking;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.BookingStatus;
import com.bookfair.backend.model.enums.ReservationStatus;
import com.bookfair.backend.repository.EventSpaceBookingRepository;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.service.QRService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class TicketingConsumerTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private EventSpaceBookingRepository bookingRepository;
    @Mock
    private QRService qrCodeService;

    @InjectMocks
    private TicketingConsumer ticketingConsumer;

    private UUID reservationId;
    private PaymentCompletedEvent event;

    @BeforeEach
    void setUp() {
        reservationId = UUID.randomUUID();
        event = new PaymentCompletedEvent(reservationId, "pi_test123", BigDecimal.valueOf(150));
    }

    private Reservation pendingReservation(Instant expiresAt) {
        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setExpiresAt(expiresAt);

        EventSpaceBooking booking = new EventSpaceBooking();
        booking.setStatus(BookingStatus.PENDING);
        reservation.setSpaceBookings(new java.util.ArrayList<>(List.of(booking)));
        return reservation;
    }

    @Test
    void confirmsReservation_whenStillPendingAndNotExpired() {
        Reservation reservation = pendingReservation(Instant.now().plus(5, ChronoUnit.MINUTES));
        when(reservationRepository.findByIdAndStatusForUpdate(reservationId, ReservationStatus.PENDING))
                .thenReturn(Optional.of(reservation));
        when(qrCodeService.generateQRCode(any())).thenReturn("base64image");

        ticketingConsumer.processTicketing(event);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getQrCodePayload()).isEqualTo("RES-" + reservationId);
        assertThat(reservation.getSpaceBookings().get(0).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(reservationRepository).save(reservation);
        verify(bookingRepository).saveAll(reservation.getSpaceBookings());
    }

    @Test
    void doesNotConfirm_whenHoldExpiredBeforeProcessing() {
        // Still PENDING (so the locked query would find it) but expiresAt is in the past —
        // this is the exact race the fix closes: payment succeeded after the hold window.
        Reservation reservation = pendingReservation(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(reservationRepository.findByIdAndStatusForUpdate(reservationId, ReservationStatus.PENDING))
                .thenReturn(Optional.of(reservation));

        ticketingConsumer.processTicketing(event);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getQrCodePayload()).isNull();
        verify(reservationRepository, never()).save(any());
        verify(bookingRepository, never()).saveAll(any());
    }

    @Test
    void skipsSilently_whenAlreadyConfirmedByAnEarlierDeliveryOfTheSameEvent() {
        // Locked PENDING-scoped read finds nothing (already CONFIRMED) — Kafka's
        // at-least-once delivery means this event could arrive twice.
        when(reservationRepository.findByIdAndStatusForUpdate(reservationId, ReservationStatus.PENDING))
                .thenReturn(Optional.empty());

        Reservation alreadyConfirmed = new Reservation();
        alreadyConfirmed.setId(reservationId);
        alreadyConfirmed.setStatus(ReservationStatus.CONFIRMED);
        alreadyConfirmed.setQrCodePayload("RES-" + reservationId);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(alreadyConfirmed));

        ticketingConsumer.processTicketing(event);

        verify(reservationRepository, never()).save(any());
        verify(qrCodeService, never()).generateQRCode(any());
    }

    @Test
    void doesNotResurrect_whenReservationWasExpiredOrCancelledByCleanupJob() {
        // Locked PENDING-scoped read finds nothing because ReservationCleanupService (or an
        // admin cancel) already moved it out of PENDING. Must NOT be force-confirmed.
        when(reservationRepository.findByIdAndStatusForUpdate(reservationId, ReservationStatus.PENDING))
                .thenReturn(Optional.empty());

        Reservation expired = new Reservation();
        expired.setId(reservationId);
        expired.setStatus(ReservationStatus.EXPIRED);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(expired));

        ticketingConsumer.processTicketing(event);

        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        verify(reservationRepository, never()).save(any());
        verify(bookingRepository, never()).saveAll(any());
    }

    @Test
    void logsAndReturns_whenReservationDoesNotExistAtAll() {
        when(reservationRepository.findByIdAndStatusForUpdate(reservationId, ReservationStatus.PENDING))
                .thenReturn(Optional.empty());
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        ticketingConsumer.processTicketing(event);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void stillConfirms_whenQrGenerationThrows() {
        // QR generation is best-effort — a QR failure must not block confirming the
        // reservation itself, since the payload string is independent of the image render.
        Reservation reservation = pendingReservation(Instant.now().plus(5, ChronoUnit.MINUTES));
        when(reservationRepository.findByIdAndStatusForUpdate(reservationId, ReservationStatus.PENDING))
                .thenReturn(Optional.of(reservation));
        when(qrCodeService.generateQRCode(any())).thenThrow(new RuntimeException("zxing boom"));

        ticketingConsumer.processTicketing(event);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository).save(reservation);
    }
}
