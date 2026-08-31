package com.bookfair.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.stall.request.CreateStallRequest;
import com.bookfair.backend.dto.stall.request.UpdateStallRequest;
import com.bookfair.backend.dto.stall.response.StallResponse;
import com.bookfair.backend.service.StallService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/stalls")
@RequiredArgsConstructor
public class StallController {

    private final StallService stallService;

    @GetMapping("/hall/{hallId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseDto<List<StallResponse>>> getStallsByHall(@PathVariable UUID hallId) {
        List<StallResponse> data = stallService.getAllStallsForHall(hallId);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Stalls fetched successfully", data, Instant.now()));
    }

    @GetMapping("/{stallId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseDto<StallResponse>> getStallById(@PathVariable UUID stallId) {
        StallResponse data = stallService.getStallById(stallId);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Stall fetched successfully", data, Instant.now()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    public ResponseEntity<ApiResponseDto<List<StallResponse>>> createStalls(@Valid @RequestBody List<CreateStallRequest> requests) {
        List<StallResponse> createdStalls = stallService.createStalls(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDto<>(true, "Stalls created successfully", createdStalls, Instant.now()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    public ResponseEntity<ApiResponseDto<StallResponse>> updateStall(@PathVariable UUID id, @Valid @RequestBody UpdateStallRequest request) {
        StallResponse data = stallService.updateStall(id, request);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Stall updated successfully", data, Instant.now()));
    }

    @PatchMapping("/{stallId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    public ResponseEntity<ApiResponseDto<StallResponse>> updateStallStatus(
            @PathVariable UUID stallId, 
            @RequestParam com.bookfair.backend.model.enums.StallActiveStatus status) {
        StallResponse data = stallService.updateStallStatus(stallId, status);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Stall status updated successfully", data, Instant.now()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    public ResponseEntity<ApiResponseDto<Void>> deactivateStall(@PathVariable UUID id) {
        stallService.deactivateStall(List.of(id));
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Stall deactivated successfully", null, Instant.now()));
    }

    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDto<Page<StallResponse>>> getAvailableStalls(
            @RequestParam UUID eventId,
            @org.springframework.data.web.PageableDefault(size = 20, sort = "name") Pageable pageable) {
        Page<StallResponse> data = stallService.getAvailableStalls(eventId, pageable);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Available stalls fetched successfully", data, Instant.now()));
    }
}
