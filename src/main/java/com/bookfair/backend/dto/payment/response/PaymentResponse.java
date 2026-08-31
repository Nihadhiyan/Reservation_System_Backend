package com.bookfair.backend.dto.payment.response;

import java.math.BigDecimal;
import java.util.UUID;
public record PaymentResponse(
    UUID id,
    UUID reservationId,
    String stripeChargeId,
    BigDecimal amount,
    String status,
    String gateway,
    String transactionId,
    String paymentUrl
) {}
