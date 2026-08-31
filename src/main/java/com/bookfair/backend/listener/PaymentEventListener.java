package com.bookfair.backend.listener;

import java.util.Objects;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bookfair.backend.config.StripeProperties;
import com.bookfair.backend.event.payment.PaymentCompletedEvent;
import com.bookfair.backend.event.reservation.ReservationCancelledByAdminEvent;
import com.bookfair.backend.event.reservation.ReservationRefundedEvent;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.PaymentStatus;
import com.bookfair.backend.repository.PaymentRepository;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.service.SettlementService;
import com.stripe.Stripe;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final SettlementService settlementService;
    private final StripeProperties stripeProperties;
    private final ApplicationEventPublisher eventPublisher;

    // Settlement (the vendor -> organizer -> venue-owner rent waterfall) was previously
    // never triggered by a real payment — SettlementService.processVendorPayment had no
    // caller anywhere in the app, so settlement records never updated from live payments.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        Objects.requireNonNull(event, "Event cannot be null");
        log.info("Processing settlement for completed payment on reservation: {}", event.reservationId());

        Reservation reservation = reservationRepository.findById(event.reservationId()).orElse(null);
        if (reservation == null) {
            log.error("Cannot process settlement — reservation {} not found for completed payment {}",
                    event.reservationId(), event.transactionId());
            return;
        }

        settlementService.processVendorPayment(reservation, event.amount());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationCancelledByAdmin(ReservationCancelledByAdminEvent event) {
        Objects.requireNonNull(event, "Event cannot be null");
        log.info("Processing async refund for cancelled reservation: {}", event.reservationId());
        
        paymentRepository.findByReservationId(event.reservationId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                boolean refundIssued = false;
                try {
                    Stripe.apiKey = stripeProperties.getApi().getKey();
                    if (payment.getTransactionId() != null && payment.getTransactionId().startsWith("cs_")) {
                        Session session = Session.retrieve(payment.getTransactionId());
                        if (session != null && session.getPaymentIntent() != null) {
                            RefundCreateParams params = RefundCreateParams.builder()
                                    .setPaymentIntent(session.getPaymentIntent())
                                    .build();
                            Refund.create(params);
                            refundIssued = true;
                        }
                    } else if (payment.getTransactionId() != null && payment.getTransactionId().startsWith("pi_")) {
                        RefundCreateParams params = RefundCreateParams.builder()
                                .setPaymentIntent(payment.getTransactionId())
                                .build();
                        Refund.create(params);
                        refundIssued = true;
                    }
                    if (refundIssued) {
                        log.info("Stripe refund triggered successfully for payment {}", payment.getId());
                    } else {
                        log.error("Payment {} has no refundable Stripe transaction id [{}]; refund NOT issued",
                                payment.getId(), payment.getTransactionId());
                    }
                } catch (Exception e) {
                    log.error("Stripe refund call failed for payment {}: {}", payment.getId(), e.getMessage(), e);
                    refundIssued = false;
                }

                if (!refundIssued) {
                    payment.setStatus(PaymentStatus.REFUND_FAILED);
                    paymentRepository.save(payment);
                    log.error("Updated payment {} status to REFUND_FAILED — requires manual review", payment.getId());
                    return;
                }

                payment.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);

                if (payment.getReservation() != null && payment.getReservation().getUser() != null) {
                    eventPublisher.publishEvent(new ReservationRefundedEvent(
                            payment.getReservation().getUser().getId(),
                            payment.getReservation().getUser().getUsername(),
                            payment.getReservation().getUser().getEmail(),
                            payment.getReservation().getId(),
                            payment.getReservation().getEvent() != null ? payment.getReservation().getEvent().getName() : "Event"));
                }
                log.info("Updated payment {} status to REFUNDED", payment.getId());
            }
        });
    }
}
