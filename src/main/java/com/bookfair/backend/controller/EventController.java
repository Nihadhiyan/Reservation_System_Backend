package com.bookfair.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;

import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.event.request.CreateEventRequest;
import com.bookfair.backend.dto.event.request.UpdateEventRequest;
import com.bookfair.backend.dto.event.response.EventResponse;

import com.bookfair.backend.service.EventService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService eventService;

    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponseDto<List<EventResponse>>> getUpcomingEvents() {
        List<EventResponse> response = eventService.getUpcomingEvents();
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Upcoming events retrieved successfully", response, Instant.now()));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<Page<EventResponse>>> getAllEvents(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<EventResponse> response = eventService.getAllEvents(pageable);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Events retrieved successfully", response, Instant.now()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<EventResponse>> getEventById(@PathVariable UUID id) {
        EventResponse response = eventService.getEventById(id);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Event retrieved successfully", response, Instant.now()));
    }



    @PreAuthorize("@orgAuth.isOrganizerAdmin(authentication, #request.organizerId())")
    @PostMapping
    public ResponseEntity<ApiResponseDto<EventResponse>> createEvent(@RequestBody @Valid CreateEventRequest request) {
        EventResponse response = eventService.createEvent(request);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Event created successfully", response, Instant.now()));
    }

    @PreAuthorize("@orgAuth.isOrganizerAdminByEvent(authentication, #id)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<EventResponse>> updateEvent(@PathVariable UUID id, @RequestBody @Valid UpdateEventRequest request) {
        EventResponse response = eventService.updateEvent(id, request);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Event updated successfully", response, Instant.now()));
    }

    @PreAuthorize("@orgAuth.isOrganizerAdminByEvent(authentication, #id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> deleteEvent(@PathVariable UUID id) {
        eventService.deleteEvent(id);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Event deleted successfully", null, Instant.now()));
    }

    @PatchMapping("/{eventId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponseDto<Void>> changeStatus(@PathVariable UUID eventId, @RequestParam String status) {
        eventService.changeStatus(eventId, status);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Event status updated successfully", null, Instant.now()));
    }
}
