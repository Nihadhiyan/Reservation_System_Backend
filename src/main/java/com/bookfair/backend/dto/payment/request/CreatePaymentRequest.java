package com.bookfair.backend.dto.payment.request;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
    @NotNull(message = "Reservation id is required")
    UUID reservationId,

    @NotBlank(message = "Stripe charge id is required")
    String stripeChargeId,

    @NotNull(message = "Amount is required")
    BigDecimal amount,

    @NotBlank(message = "Status is required")
    String status
) {}
