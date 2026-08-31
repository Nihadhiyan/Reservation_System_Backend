package com.bookfair.backend.controller;

import com.bookfair.backend.dto.event.request.CreateEventSpaceBookingRequest;
import com.bookfair.backend.dto.event.request.CreateStallBookingRequest;
import com.bookfair.backend.dto.event.response.EventSpaceBookingResponse;
import com.bookfair.backend.service.EventSpaceBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}")
@RequiredArgsConstructor
public class EventBookingController {

    private final EventSpaceBookingService bookingService;

    @PostMapping("/space-bookings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EventSpaceBookingResponse>> createSpaceBookings(
            @PathVariable UUID eventId,
            @RequestAttribute("userId") UUID userId,
            @Valid @RequestBody CreateEventSpaceBookingRequest request) {
        
        List<EventSpaceBookingResponse> response = bookingService.createSpaceBookings(userId, eventId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stall-bookings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EventSpaceBookingResponse>> createStallBookings(
            @PathVariable UUID eventId,
            @RequestAttribute("userId") UUID userId,
            @Valid @RequestBody CreateStallBookingRequest request) {
        
        List<EventSpaceBookingResponse> response = bookingService.createStallBookings(userId, eventId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/bookings/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable UUID eventId,
            @PathVariable UUID bookingId,
            @RequestAttribute("userId") UUID userId) {
        
        bookingService.cancelBooking(userId, eventId, bookingId);
        return ResponseEntity.noContent().build();
    }
}
