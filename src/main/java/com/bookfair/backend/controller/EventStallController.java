package com.bookfair.backend.controller;

import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.eventstall.request.CreateEventStallRequest;
import com.bookfair.backend.dto.eventstall.request.UpdateEventStallRequest;
import com.bookfair.backend.dto.eventstall.response.EventStallResponse;
import com.bookfair.backend.service.EventStallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/stalls")
@RequiredArgsConstructor
public class EventStallController {

    private final EventStallService eventStallService;

    // VENDOR — browse available stalls for booking
    // Returns only active + available stalls
    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDto<List<EventStallResponse>>> getAvailableStalls(
            @PathVariable UUID eventId) {
        List<EventStallResponse> data =
            eventStallService.getAvailableStallsForEvent(eventId);
        return ResponseEntity.ok(new ApiResponseDto<>(
            true, "Available stalls fetched", data, Instant.now()));
    }

    // VENDOR — hall layout map view (active stalls only)
    // Frontend uses this to render the color-coded stall map
    @GetMapping("/hall/{hallId}/layout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDto<List<EventStallResponse>>> getHallLayout(
            @PathVariable UUID eventId,
            @PathVariable UUID hallId) {
        List<EventStallResponse> data =
            eventStallService.getEventLayoutForHall(eventId, hallId);
        return ResponseEntity.ok(new ApiResponseDto<>(
            true, "Hall layout fetched", data, Instant.now()));
    }

    // ORGANIZER — full hall layout including disabled stalls
    // For the organizer's management dashboard
    @GetMapping("/hall/{hallId}/manage")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isOrganizerAdmin(authentication, #eventId)")
    public ResponseEntity<ApiResponseDto<List<EventStallResponse>>> getFullHallLayout(
            @PathVariable UUID eventId,
            @PathVariable UUID hallId) {
        List<EventStallResponse> data =
            eventStallService.getFullEventLayoutForHall(eventId, hallId);
        return ResponseEntity.ok(new ApiResponseDto<>(
            true, "Full hall layout fetched", data, Instant.now()));
    }

    // ORGANIZER — add a stall to the event with optional customization
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isOrganizerAdmin(authentication, #eventId)")
    public ResponseEntity<ApiResponseDto<EventStallResponse>> addStallToEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateEventStallRequest request) {
        EventStallResponse data =
            eventStallService.addStallToEvent(eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDto<>(
            true, "Stall added to event", data, Instant.now()));
    }

    // ORGANIZER — update stall configuration for this event
    // Can disable, reposition, relabel, or reprice a stall
    @PutMapping("/{stallId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isOrganizerAdmin(authentication, #eventId)")
    public ResponseEntity<ApiResponseDto<EventStallResponse>> updateEventStall(
            @PathVariable UUID eventId,
            @PathVariable UUID stallId,
            @Valid @RequestBody UpdateEventStallRequest request) {
        EventStallResponse data =
            eventStallService.updateEventStall(eventId, stallId, request);
        return ResponseEntity.ok(new ApiResponseDto<>(
            true, "Event stall updated", data, Instant.now()));
    }
}
