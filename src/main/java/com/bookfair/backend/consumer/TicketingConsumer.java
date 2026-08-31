package com.bookfair.backend.consumer;

import com.bookfair.backend.event.payment.PaymentCompletedEvent;
import com.bookfair.backend.model.EventSpaceBooking;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.BookingStatus;
import com.bookfair.backend.model.enums.ReservationStatus;
import com.bookfair.backend.repository.EventSpaceBookingRepository;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.service.QRService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketingConsumer {

    private final ReservationRepository reservationRepository;
    private final EventSpaceBookingRepository bookingRepository;
    private final QRService qrCodeService;

    @KafkaListener(topics = "${clausis.kafka.topics.payment-completed:payment-completed-topic}", groupId = "ticketing-group")
    @Transactional
    public void processTicketing(PaymentCompletedEvent event) {
        log.info("TicketingConsumer received payment event for reservation: {}", event.reservationId());

        // Locked read scoped to PENDING — mirrors ReservationService.confirmReservation and
        // ReservationCleanupService's own locking pattern, so this consumer and the expiry
        // cleanup job can never both act on the same reservation: whichever locks the row
        // first wins, and the loser's status-scoped query simply finds nothing to act on.
        Reservation reservation = reservationRepository
                .findByIdAndStatusForUpdate(event.reservationId(), ReservationStatus.PENDING)
                .orElse(null);

        if (reservation == null) {
            handleNotPending(event);
            return;
        }

        if (reservation.getExpiresAt().isBefore(java.time.Instant.now())) {
            log.error("CRITICAL: Payment {} completed for reservation {} but its hold expired at {} before "
                            + "ticketing could process it — the customer was charged but has no valid booking. "
                            + "This requires manual review / refund; it will NOT be silently confirmed.",
                    event.transactionId(), event.reservationId(), reservation.getExpiresAt());
            return;
        }

        confirmReservationAndBookings(reservation);
        log.info("Ticket successfully generated and reservation {} confirmed", event.reservationId());
    }

    // The locked PENDING-scoped read above found nothing — either the reservation doesn't
    // exist, it's already been confirmed by an earlier (at-least-once) delivery of this same
    // event, or it's in some other terminal state (EXPIRED/CANCELLED/REFUNDED). Only the
    // "already confirmed by us" case is a safe no-op; every other case means a payment
    // succeeded for a reservation that's no longer valid to fulfill, and must not be
    // silently resurrected back into CONFIRMED.
    private void handleNotPending(PaymentCompletedEvent event) {
        Reservation current = reservationRepository.findById(event.reservationId()).orElse(null);

        if (current == null) {
            log.error("Reservation {} not found for completed payment {}", event.reservationId(), event.transactionId());
            return;
        }

        if (current.getStatus() == ReservationStatus.CONFIRMED && current.getQrCodePayload() != null) {
            log.info("Ticket already generated for reservation {}. Skipping.", event.reservationId());
            return;
        }

        log.error("CRITICAL: Payment {} completed for reservation {} but it is no longer PENDING (status={}) — "
                        + "the customer was charged but has no valid booking. This requires manual review / "
                        + "refund; it will NOT be silently confirmed.",
                event.transactionId(), event.reservationId(), current.getStatus());
    }

    private void confirmReservationAndBookings(Reservation reservation) {
        reservation.setStatus(ReservationStatus.CONFIRMED);
        String qrPayload = "RES-" + reservation.getId();
        reservation.setQrCodePayload(qrPayload);

        try {
            qrCodeService.generateQRCode(qrPayload);
        } catch (Exception e) {
            log.error("Failed to generate QR code for reservation {}", reservation.getId(), e);
            // Even if QR generation fails visually, we mark as confirmed and payload is set
        }

        for (EventSpaceBooking b : reservation.getSpaceBookings()) {
            b.setStatus(BookingStatus.CONFIRMED);
        }
        bookingRepository.saveAll(reservation.getSpaceBookings());

        reservationRepository.save(reservation);
    }
}
