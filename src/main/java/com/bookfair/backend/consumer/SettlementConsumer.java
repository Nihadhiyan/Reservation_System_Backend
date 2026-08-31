package com.bookfair.backend.consumer;

import com.bookfair.backend.event.payment.PaymentCompletedEvent;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementConsumer {

    private final ReservationRepository reservationRepository;
    private final SettlementService settlementService;

    @KafkaListener(topics = "${clausis.kafka.topics.payment-completed:payment-completed-topic}", groupId = "settlement-group")
    @Transactional
    public void processSettlement(PaymentCompletedEvent event) {
        log.info("SettlementConsumer received payment event for reservation: {}", event.reservationId());

        Reservation reservation = reservationRepository.findById(event.reservationId()).orElse(null);
        if (reservation == null) {
            log.error("Cannot process settlement — reservation {} not found for completed payment {}",
                    event.reservationId(), event.transactionId());
            return;
        }

        // Idempotency: SettlementService already handles duplicates or we can check here.
        // Assuming SettlementService handles it, or we could add a check if needed.
        settlementService.processVendorPayment(reservation, event.amount());
        log.info("Settlement processed successfully for reservation {}", event.reservationId());
    }
}
