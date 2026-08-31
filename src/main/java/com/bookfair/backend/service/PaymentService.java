package com.bookfair.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.payment.mapper.PaymentMapper;
import com.bookfair.backend.dto.payment.request.CreatePaymentRequest;
import com.bookfair.backend.dto.payment.response.PaymentResponse;
import com.bookfair.backend.event.payment.PaymentCompletedEvent;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.integration.payment.PaymentGateway;
import com.bookfair.backend.integration.payment.PaymentGateway.PaymentWebhookResult;
import com.bookfair.backend.model.Payment;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.PaymentStatus;
import com.bookfair.backend.repository.PaymentRepository;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.producer.PaymentEventProducer;
import org.springframework.beans.factory.annotation.Value;
import static java.util.Objects.requireNonNull;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final PricingEngineService pricingEngineService;
    private final PaymentMapper paymentMapper;
    private final List<PaymentGateway> paymentGateways;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentEventProducer paymentEventProducer;

    @Value("${clausis.features.kafka-enabled:true}")
    private boolean isKafkaEnabled;

    @Transactional
    public PaymentResponse initializePayment(CreatePaymentRequest request, String gatewayType) {
        requireNonNull(request, "request cannot be null");
        Reservation reservation = reservationRepository.findById(requireNonNull(request.reservationId()))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Reservation not found", ErrorCode.RESERVATION_NOT_FOUND));

        if (reservation.getStatus() != com.bookfair.backend.model.enums.ReservationStatus.PENDING) {
            throw new BusinessException(
                    "Cannot initialize payment for a reservation that is " + reservation.getStatus()
                            + " — only PENDING reservations can be paid for.",
                    ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        java.math.BigDecimal calculatedTotal = reservation.getTotalPrice();
        if (request.amount().compareTo(calculatedTotal) != 0) {
            throw new BusinessException(
                    "Price mismatch: requested " + request.amount() + " but calculated " + calculatedTotal,
                    ErrorCode.PRICE_MISMATCH);
        }

        PaymentGateway adapter = paymentGateways.stream()
                .filter(gateway -> gateway.supports(requireNonNull(gatewayType)))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unsupported payment gateway: " + gatewayType,
                        ErrorCode.BUSINESS_RULE_VIOLATION));

        PaymentResponse response = adapter.initializePayment(request);

        Payment payment = paymentMapper.toPayment(reservation, request.amount(), response.transactionId(),
                com.bookfair.backend.model.enums.CurrencyCode.USD, gatewayType);

        Payment saved = paymentRepository.save(requireNonNull(payment));
        log.info("Initialized payment for reservation {} via {}", reservation.getId(), gatewayType);

        return paymentMapper.toPaymentResponse(saved);
    }

    @Transactional
    public void processWebhook(String payload, String signatureHeader, String gatewayType) {
        PaymentGateway adapter = paymentGateways.stream()
                .filter(gateway -> gateway.supports(requireNonNull(gatewayType)))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unsupported payment gateway: " + gatewayType,
                        ErrorCode.BUSINESS_RULE_VIOLATION));

        PaymentWebhookResult result = adapter.processWebhook(payload, signatureHeader);

        if (!result.isValid() || result.transactionId() == null) {
            log.warn("Ignored or invalid webhook from gateway {}", gatewayType);
            return;
        }

        Payment payment = paymentRepository.findByTransactionIdForUpdate(requireNonNull(result.transactionId()))
                .orElseGet(() -> {
                    // Fallback to searching by reservationId if transaction ID wasn't saved yet
                    Reservation reservation = reservationRepository.findById(requireNonNull(result.reservationId()))
                            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found",
                                    ErrorCode.RESERVATION_NOT_FOUND));

                    return paymentMapper.toWebhookPayment(reservation, result.transactionId(), result.amount(),
                            com.bookfair.backend.model.enums.CurrencyCode.USD, gatewayType);
                });

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("Idempotency Check: Payment {} is already COMPLETED. Ignoring webhook.", payment.getId());
            return;
        }

        PaymentStatus status = PaymentStatus.valueOf(result.paymentStatus().toUpperCase());
        payment.setStatus(status);

        Payment saved = paymentRepository.save(payment);
        log.info("Processed webhook for payment {}", saved.getId());

        if (status == PaymentStatus.COMPLETED) {
            PaymentCompletedEvent paymentCompletedEvent = new PaymentCompletedEvent(
                    requireNonNull(payment.getReservation().getId()),
                    requireNonNull(saved.getTransactionId()),
                    requireNonNull(saved.getAmount()));
                    
            if (isKafkaEnabled) {
                log.info("Kafka is enabled, emitting PaymentCompletedEvent via Kafka Producer");
                paymentEventProducer.publishPaymentCompletedEvent(paymentCompletedEvent);
            } else {
                log.info("Kafka is disabled, emitting PaymentCompletedEvent via Spring EventPublisher");
                eventPublisher.publishEvent(paymentCompletedEvent);
            }
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentStatus(UUID paymentId) {
        Payment payment = paymentRepository.findById(requireNonNull(paymentId))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Payment not found", ErrorCode.PAYMENT_NOT_FOUND));

        return paymentMapper.toPaymentResponse(payment);
    }
}
