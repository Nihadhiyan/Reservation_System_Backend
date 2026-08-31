package com.bookfair.backend.service;

import com.bookfair.backend.dto.payment.mapper.PaymentMapper;
import com.bookfair.backend.dto.payment.request.CreatePaymentRequest;
import com.bookfair.backend.dto.payment.response.PaymentResponse;
import com.bookfair.backend.event.payment.PaymentCompletedEvent;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.integration.payment.PaymentGateway;
import com.bookfair.backend.integration.payment.PaymentGateway.PaymentWebhookResult;
import com.bookfair.backend.model.Payment;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.CurrencyCode;
import com.bookfair.backend.model.enums.PaymentStatus;
import com.bookfair.backend.model.enums.ReservationStatus;
import com.bookfair.backend.producer.PaymentEventProducer;
import com.bookfair.backend.repository.PaymentRepository;
import com.bookfair.backend.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PricingEngineService pricingEngineService;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PaymentGateway stripeGateway;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PaymentEventProducer paymentEventProducer;

    private PaymentService paymentService;

    private UUID reservationId;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, reservationRepository, pricingEngineService,
                paymentMapper, List.of(stripeGateway), eventPublisher, paymentEventProducer);
        // isKafkaEnabled is a @Value-injected field with no test property source here.
        ReflectionTestUtils.setField(paymentService, "isKafkaEnabled", false);

        reservationId = UUID.randomUUID();
        reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setTotalPrice(BigDecimal.valueOf(150));
    }

    @Test
    void initializePayment_rejectsNonPendingReservation() {
        reservation.setStatus(ReservationStatus.CANCELLED);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        CreatePaymentRequest request = new CreatePaymentRequest(reservationId, "ch_1", BigDecimal.valueOf(150), "PENDING");

        assertThatThrownBy(() -> paymentService.initializePayment(request, "STRIPE"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(stripeGateway);
    }

    @Test
    void initializePayment_rejectsPriceMismatch() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        CreatePaymentRequest request = new CreatePaymentRequest(reservationId, "ch_1", BigDecimal.valueOf(999), "PENDING");

        assertThatThrownBy(() -> paymentService.initializePayment(request, "STRIPE"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(stripeGateway);
    }

    @Test
    void initializePayment_succeedsForPendingReservationWithMatchingPrice() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(stripeGateway.supports("STRIPE")).thenReturn(true);
        PaymentResponse gatewayResponse = new PaymentResponse(null, reservationId, null,
                BigDecimal.valueOf(150), "PENDING", "STRIPE", "cs_test123", "https://stripe/session");
        when(stripeGateway.initializePayment(any())).thenReturn(gatewayResponse);

        Payment mappedPayment = new Payment();
        when(paymentMapper.toPayment(eq(reservation), eq(BigDecimal.valueOf(150)), eq("cs_test123"),
                eq(CurrencyCode.USD), eq("STRIPE"))).thenReturn(mappedPayment);
        when(paymentRepository.save(mappedPayment)).thenReturn(mappedPayment);

        CreatePaymentRequest request = new CreatePaymentRequest(reservationId, "ch_1", BigDecimal.valueOf(150), "PENDING");
        paymentService.initializePayment(request, "STRIPE");

        verify(paymentRepository).save(mappedPayment);
    }

    @Test
    void processWebhook_ignoresInvalidWebhook() {
        when(stripeGateway.supports("STRIPE")).thenReturn(true);
        when(stripeGateway.processWebhook(any(), any()))
                .thenReturn(new PaymentWebhookResult(false, null, null, null, null));

        paymentService.processWebhook("payload", "sig", "STRIPE");

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void processWebhook_isIdempotent_whenPaymentAlreadyCompleted() {
        when(stripeGateway.supports("STRIPE")).thenReturn(true);
        when(stripeGateway.processWebhook(any(), any()))
                .thenReturn(new PaymentWebhookResult(true, "cs_test123", "COMPLETED", BigDecimal.valueOf(150), reservationId));

        Payment existing = new Payment();
        existing.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findByTransactionIdForUpdate("cs_test123")).thenReturn(Optional.of(existing));

        paymentService.processWebhook("payload", "sig", "STRIPE");

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(paymentEventProducer);
    }

    @Test
    void processWebhook_publishesViaSpringEvent_whenKafkaDisabled() {
        when(stripeGateway.supports("STRIPE")).thenReturn(true);
        when(stripeGateway.processWebhook(any(), any()))
                .thenReturn(new PaymentWebhookResult(true, "cs_test123", "COMPLETED", BigDecimal.valueOf(150), reservationId));

        Payment existing = new Payment();
        existing.setStatus(PaymentStatus.PENDING);
        existing.setReservation(reservation);
        existing.setTransactionId("cs_test123");
        existing.setAmount(BigDecimal.valueOf(150));
        when(paymentRepository.findByTransactionIdForUpdate("cs_test123")).thenReturn(Optional.of(existing));
        when(paymentRepository.save(existing)).thenReturn(existing);

        paymentService.processWebhook("payload", "sig", "STRIPE");

        verify(eventPublisher).publishEvent(any(PaymentCompletedEvent.class));
        verifyNoInteractions(paymentEventProducer);
    }

    @Test
    void processWebhook_publishesViaKafka_whenKafkaEnabled() {
        ReflectionTestUtils.setField(paymentService, "isKafkaEnabled", true);

        when(stripeGateway.supports("STRIPE")).thenReturn(true);
        when(stripeGateway.processWebhook(any(), any()))
                .thenReturn(new PaymentWebhookResult(true, "cs_test123", "COMPLETED", BigDecimal.valueOf(150), reservationId));

        Payment existing = new Payment();
        existing.setStatus(PaymentStatus.PENDING);
        existing.setReservation(reservation);
        existing.setTransactionId("cs_test123");
        existing.setAmount(BigDecimal.valueOf(150));
        when(paymentRepository.findByTransactionIdForUpdate("cs_test123")).thenReturn(Optional.of(existing));
        when(paymentRepository.save(existing)).thenReturn(existing);

        paymentService.processWebhook("payload", "sig", "STRIPE");

        verify(paymentEventProducer).publishPaymentCompletedEvent(any(PaymentCompletedEvent.class));
        verifyNoInteractions(eventPublisher);
    }
}
