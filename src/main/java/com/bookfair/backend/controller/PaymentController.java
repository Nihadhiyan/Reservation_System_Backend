package com.bookfair.backend.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.payment.request.CreatePaymentRequest;
import com.bookfair.backend.dto.payment.response.PaymentResponse;
import com.bookfair.backend.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initialize")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseDto<PaymentResponse>> initializePayment(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse data = paymentService.initializePayment(request, "STRIPE");
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDto<>(true, "Payment initialized successfully", data, Instant.now()));
    }

    // Webhook handling lives solely in StripeWebhookController (/api/payments/webhook) —
    // having two endpoints call into PaymentService.processWebhook risked duplicate
    // processing if both were ever registered with Stripe.

    @GetMapping("/{paymentId}/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseDto<PaymentResponse>> getPaymentStatus(@PathVariable UUID paymentId) {
        PaymentResponse data = paymentService.getPaymentStatus(paymentId);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Payment status fetched successfully", data, Instant.now()));
    }
}
