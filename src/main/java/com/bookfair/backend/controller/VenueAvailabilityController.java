package com.bookfair.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.bookfair.backend.dto.event.request.CreateEventSpaceBookingRequest;
import com.bookfair.backend.exception.ConflictDetail;

import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.venue.response.HallAvailabilityDto;
import com.bookfair.backend.dto.venue.response.VenueAvailabilityResponse;
import com.bookfair.backend.service.VenueAvailabilityService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueAvailabilityController {

    private final VenueAvailabilityService venueAvailabilityService;

    @GetMapping("/{venueId}/availability")
    public ResponseEntity<ApiResponseDto<VenueAvailabilityResponse>> checkVenueAvailability(
            @PathVariable UUID venueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {

        VenueAvailabilityResponse data = 
            venueAvailabilityService.checkVenueAvailability(venueId, start, end);

        return ResponseEntity.ok(new ApiResponseDto<>(
            true, "Venue availability checked", data, Instant.now()));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponseDto<List<VenueAvailabilityResponse>>> findAvailableVenues(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @PageableDefault(size = 20) Pageable pageable) {

        List<VenueAvailabilityResponse> data = 
            venueAvailabilityService.findAvailableVenues(start, end, pageable);

        return ResponseEntity.ok(new ApiResponseDto<>(
            true, "Available venues fetched", data, Instant.now()));
    }

    @GetMapping("/{venueId}/availability/halls")
    public ResponseEntity<ApiResponseDto<List<HallAvailabilityDto>>> getHallAvailability(
            @PathVariable UUID venueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {

        List<HallAvailabilityDto> data = 
            venueAvailabilityService.getHallAvailabilityForVenue(venueId, start, end);

        return ResponseEntity.ok(
            new ApiResponseDto<>(true, "Hall availability fetched", data, Instant.now()));
    }

    @PostMapping("/check-availability")
    public ResponseEntity<ApiResponseDto<List<ConflictDetail>>> checkAvailability(
            @RequestParam(required = false) UUID eventId,
            @Valid @RequestBody CreateEventSpaceBookingRequest request) {

        List<ConflictDetail> conflicts = venueAvailabilityService.checkMultiSpaceAvailability(eventId, request);

        return ResponseEntity.ok(
            new ApiResponseDto<>(true, "Availability checked", conflicts, Instant.now()));
    }
}
