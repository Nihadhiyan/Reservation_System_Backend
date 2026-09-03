package com.bookfair.backend.consumer;

import com.bookfair.backend.event.payment.PaymentCompletedEvent;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.ReservationStatus;
import com.bookfair.backend.model.enums.TaskType;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.service.NotificationService;
import com.bookfair.backend.service.QRService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailConsumer {

    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;
    private final QRService qrCodeService;

    @KafkaListener(topics = "${clausis.kafka.topics.payment-completed:payment-completed-topic}", groupId = "email-group")
    @Transactional
    public void sendConfirmationEmail(PaymentCompletedEvent event) {
        log.info("EmailConsumer received payment event for reservation: {}", event.reservationId());

        Reservation reservation = reservationRepository.findById(event.reservationId()).orElse(null);
        if (reservation == null) {
            log.error("Reservation {} not found", event.reservationId());
            return;
        }

        boolean stillPendingAndValid = reservation.getStatus() == ReservationStatus.PENDING
                && reservation.getExpiresAt() != null
                && reservation.getExpiresAt().isAfter(java.time.Instant.now());
        boolean alreadyConfirmed = reservation.getStatus() == ReservationStatus.CONFIRMED;
        if (!stillPendingAndValid && !alreadyConfirmed) {
            log.error("Skipping confirmation email for reservation {} — status is {} (payment {} likely "
                            + "completed after the hold expired), not a valid booking to confirm.",
                    reservation.getId(), reservation.getStatus(), event.transactionId());
            return;
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("userName", reservation.getUser().getUsername());
        vars.put("eventName", reservation.getEvent().getName());
        
        try {
            // Because consumers are decoupled, we dynamically generate the QR base64 here
            // to avoid race conditions with TicketingConsumer
            String qrPayload = "RES-" + reservation.getId();
            String qrCodeImage = qrCodeService.generateQRCode(qrPayload);
            vars.put("qrCodeBase64", qrCodeImage);
        } catch (Exception e) {
            log.warn("Failed to generate QR code for email", e);
        }

        notificationService.notify(reservation.getUser().getEmail(), 
                                   "Reservation Confirmed - Your Ticket", 
                                   "confirmed", 
                                   vars, 
                                   reservation.getId(), 
                                   TaskType.EMAIL_RESERVATION_CONFIRMATION);
                                   
        log.info("Confirmation email triggered for reservation {}", event.reservationId());
    }
}
