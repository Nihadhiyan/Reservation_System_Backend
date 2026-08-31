package com.bookfair.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.reservation.request.CreateReservationRequest;
import com.bookfair.backend.dto.reservation.response.ReservationDetailResponse;
import com.bookfair.backend.dto.reservation.response.ReservationResponse;
import com.bookfair.backend.service.ReservationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<List<ReservationResponse>>> getMyReservations(Authentication authentication) {
        List<ReservationResponse> response = reservationService.getMyReservations(authentication.getName());
        return ResponseEntity.ok(
                new ApiResponseDto<>(true, "My reservations retrieved successfully", response, Instant.now()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponseDto<ReservationResponse>> createReservation(
            @Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse response = reservationService.createReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>(true, "Reservation created successfully", response, Instant.now()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<ApiResponseDto<Void>> cancelReservation(@PathVariable UUID reservationId) {
        reservationService.requestCancellation(reservationId);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Reservation cancelled successfully", null, Instant.now()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponseDto<ReservationResponse>> getReservationById(@PathVariable UUID reservationId) {
        ReservationResponse response = reservationService.getReservationById(reservationId);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Reservation retrieved successfully", response, Instant.now()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{reservationId}/details")
    public ResponseEntity<ApiResponseDto<ReservationDetailResponse>> getReservationDetails(
            @PathVariable UUID reservationId) {
        ReservationDetailResponse response = reservationService.getReservationDetails(reservationId);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Reservation details retrieved successfully", response,
                Instant.now()));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponseDto<Page<ReservationResponse>>> getAllReservations(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<ReservationResponse> response = reservationService.getAllReservations(pageable);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Reservations retrieved successfully", response, Instant.now()));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<ApiResponseDto<Void>> confirmReservation(@PathVariable UUID reservationId) {
        reservationService.confirmReservation(reservationId);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Reservation confirmed successfully", null, Instant.now()));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    @PostMapping("/{reservationId}/refund")
    public ResponseEntity<ApiResponseDto<Void>> approveRefund(@PathVariable UUID reservationId) {
        reservationService.approveRefund(reservationId);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Refund approved successfully", null, Instant.now()));
    }
}
