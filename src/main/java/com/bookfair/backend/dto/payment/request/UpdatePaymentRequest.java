package com.bookfair.backend.dto.payment.request;


import jakarta.validation.constraints.NotBlank;

public record UpdatePaymentRequest(
    @NotBlank(message = "Status is required")
    String status
) {}
