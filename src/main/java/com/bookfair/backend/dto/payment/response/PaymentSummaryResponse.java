package com.bookfair.backend.dto.payment.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentSummaryResponse(
    UUID id,
    UUID reservationId,
    BigDecimal amount,
    String status
) {}
