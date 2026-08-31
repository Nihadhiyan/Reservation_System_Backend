package com.bookfair.backend.integration.payment.gateways;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.bookfair.backend.config.StripeProperties;

import com.bookfair.backend.dto.payment.request.CreatePaymentRequest;
import com.bookfair.backend.dto.payment.response.PaymentResponse;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.PaymentException;
import com.bookfair.backend.integration.payment.PaymentGateway;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData;

@Component
@RequiredArgsConstructor
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private final StripeProperties stripeProperties;

    @Override
    public PaymentResponse initializePayment(CreatePaymentRequest request) {
        try {
            Stripe.apiKey = stripeProperties.getApi().getKey();

            long amountInCents = request.amount().multiply(BigDecimal.valueOf(100)).longValue();
            String productName = "Reservation ID: " + request.reservationId();

            List<LineItem> lineItems = Arrays.asList(
                    new LineItem.Builder()
                            .setQuantity(1L)
                            .setPriceData(PriceData.builder()
                                    .setCurrency("usd")
                                    .setUnitAmount(amountInCents)
                                    .setProductData(ProductData.builder()
                                            .setName(productName)
                                            .build())
                                    .build())
                            .build());

            SessionCreateParams sessionParams = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(stripeProperties.getCheckout().getSuccessUrl())
                    .setCancelUrl(stripeProperties.getCheckout().getCancelUrl())
                    .addAllLineItem(lineItems)
                    .putMetadata("reservationId", request.reservationId().toString())
                    .build();

            Session session = Session.create(sessionParams);

            return new PaymentResponse(null, request.reservationId(), null, request.amount(), "PENDING", "STRIPE", session.getId(), session.getUrl());

        } catch (StripeException e) {
            throw new PaymentException("Stripe initialization failed: " + e.getMessage(), e, ErrorCode.PAYMENT_FAILED);
        }
    }

    @Override
    public boolean supports(String gatewayType) {
        return "STRIPE".equalsIgnoreCase(gatewayType);
    }

    @Override
    public PaymentWebhookResult processWebhook(String payload, String signatureHeader) {
        try {
            Event event = Webhook.constructEvent(payload, signatureHeader, stripeProperties.getWebhook().getSecret());

            if ("checkout.session.completed".equals(event.getType())) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                if (dataObjectDeserializer.getObject().isPresent()) {
                    StripeObject stripeObject = dataObjectDeserializer.getObject().orElseThrow(() -> new IllegalStateException("Deserialized object missing"));
                    if (stripeObject instanceof Session session) {
                        UUID reservationId = UUID.fromString(session.getMetadata().get("reservationId"));
                        BigDecimal amount = BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100));
                        String paymentStatus = "COMPLETED";
                        
                        return new PaymentWebhookResult(true, session.getId(), paymentStatus, amount, reservationId);
                    }
                }
            }
            
            return new PaymentWebhookResult(true, null, "IGNORED", null, null);
        } catch (SignatureVerificationException e) {
            log.error("Webhook verification failed: {}", e.getMessage());
            return new PaymentWebhookResult(false, null, "FAILED", null, null);
        }
    }
}
