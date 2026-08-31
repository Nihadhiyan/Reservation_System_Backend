package com.bookfair.backend.consumer;

import com.bookfair.backend.event.payment.PaymentCompletedEvent;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.User;
import com.bookfair.backend.model.enums.ReservationStatus;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.service.NotificationService;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the consistency fix: EmailConsumer must not send a "Reservation
 * Confirmed" email for a reservation that TicketingConsumer would refuse (or
 * has refused) to confirm — the two consumers run independently (separate
 * Kafka consumer groups, no ordering guarantee) so each must reach the same
 * conclusion on its own.
 */
@ExtendWith(MockitoExtension.class)
class EmailConsumerTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private QRService qrCodeService;

    @InjectMocks
    private EmailConsumer emailConsumer;

    private UUID reservationId;
    private PaymentCompletedEvent event;

    @BeforeEach
    void setUp() {
        reservationId = UUID.randomUUID();
        event = new PaymentCompletedEvent(reservationId, "pi_test123", BigDecimal.valueOf(150));
    }

    private Reservation reservationWith(ReservationStatus status, Instant expiresAt) {
        User user = new User();
        user.setUsername("vendor1");
        user.setEmail("vendor1@example.com");

        Event bookedEvent = new Event();
        bookedEvent.setName("Spring Book Fair");

        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setStatus(status);
        reservation.setExpiresAt(expiresAt);
        reservation.setUser(user);
        reservation.setEvent(bookedEvent);
        return reservation;
    }

    @Test
    void sendsEmail_whenStillPendingAndNotExpired() {
        Reservation reservation = reservationWith(ReservationStatus.PENDING, Instant.now().plus(5, ChronoUnit.MINUTES));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(qrCodeService.generateQRCode(any())).thenReturn("base64image");

        emailConsumer.sendConfirmationEmail(event);

        verify(notificationService).notify(eq("vendor1@example.com"), any(), any(), anyMap(), eq(reservationId), any());
    }

    @Test
    void sendsEmail_whenAlreadyConfirmed() {
        Reservation reservation = reservationWith(ReservationStatus.CONFIRMED, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(qrCodeService.generateQRCode(any())).thenReturn("base64image");

        emailConsumer.sendConfirmationEmail(event);

        verify(notificationService).notify(eq("vendor1@example.com"), any(), any(), anyMap(), eq(reservationId), any());
    }

    @Test
    void doesNotSendEmail_whenHoldExpiredAndStillPending() {
        // Same race TicketingConsumer refuses: payment succeeded after the hold expired.
        Reservation reservation = reservationWith(ReservationStatus.PENDING, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        emailConsumer.sendConfirmationEmail(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void doesNotSendEmail_whenReservationExpiredOrCancelled() {
        Reservation reservation = reservationWith(ReservationStatus.EXPIRED, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        emailConsumer.sendConfirmationEmail(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void doesNothing_whenReservationNotFound() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        emailConsumer.sendConfirmationEmail(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void stillSendsEmail_whenQrGenerationFails() {
        Reservation reservation = reservationWith(ReservationStatus.PENDING, Instant.now().plus(5, ChronoUnit.MINUTES));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(qrCodeService.generateQRCode(any())).thenThrow(new RuntimeException("zxing boom"));

        emailConsumer.sendConfirmationEmail(event);

        verify(notificationService).notify(eq("vendor1@example.com"), any(), any(), anyMap(), eq(reservationId), any());
    }
}
