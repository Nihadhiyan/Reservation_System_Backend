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
import org.springframework.web.bind.annotation.RestController;

import com.bookfair.backend.dto.building.response.BuildingResponse;
import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.venue.request.CreateVenueRequest;
import com.bookfair.backend.dto.venue.request.UpdateVenueRequest;
import com.bookfair.backend.dto.venue.response.VenueMapResponse;
import com.bookfair.backend.dto.venue.response.VenueResponse;
import com.bookfair.backend.dto.common.LayoutMarkerDto;
import com.bookfair.backend.service.VenueService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final VenueService venueService;

    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdmin(authentication, #request.ownerOrganizationId())")
    @PostMapping
    public ResponseEntity<ApiResponseDto<VenueResponse>> createVenue(@RequestBody @Valid CreateVenueRequest request) {
        VenueResponse response = venueService.createVenue(request);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Venue created successfully", response, Instant.now()));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<Page<VenueResponse>>> getAllVenues(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<VenueResponse> response = venueService.getAllVenues(pageable);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Venues retrieved successfully", response, Instant.now()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<VenueResponse>> getVenue(@PathVariable UUID id) {
        VenueResponse response = venueService.getVenue(id);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Venue retrieved successfully", response, Instant.now()));
    }

    @GetMapping("/{id}/map")
    public ResponseEntity<ApiResponseDto<VenueMapResponse>> getVenueMap(@PathVariable UUID id) {
        VenueMapResponse response = venueService.getVenueMap(id);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Venue map retrieved successfully", response, Instant.now()));
    }

    @PreAuthorize("@orgAuth.isVenueOwnerAdminByVenue(authentication, #id)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<VenueResponse>> updateVenue(@PathVariable UUID id,
            @Valid @RequestBody UpdateVenueRequest request) {
        VenueResponse response = venueService.updateVenue(id, request);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Venue updated successfully", response, Instant.now()));
    }

    @PreAuthorize("@orgAuth.isVenueOwnerAdminByVenue(authentication, #id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> deleteVenue(@PathVariable UUID id) {
        venueService.deleteVenue(id);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Venue deactivated successfully", null, Instant.now()));
    }

    // Manual review of a venue's Premise ID by a super admin — same workflow
    // as POST /organizations/{id}/verify for the business registration number.
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{id}/verify")
    public ResponseEntity<ApiResponseDto<Void>> verifyVenue(@PathVariable UUID id) {
        venueService.verifyVenue(id);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Venue verified successfully", null, Instant.now()));
    }

    @GetMapping("/{venueId}/buildings")
    public ResponseEntity<ApiResponseDto<List<BuildingResponse>>> getBuildingsByVenue(@PathVariable UUID venueId) {
        List<BuildingResponse> response = venueService.getBuildingsByVenue(venueId);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Buildings retrieved successfully", response, Instant.now()));
    }

    @GetMapping("/{venueId}/markers")
    public ResponseEntity<ApiResponseDto<List<LayoutMarkerDto>>> getVenueMarkers(@PathVariable UUID venueId) {
        List<LayoutMarkerDto> response = venueService.getMarkersByVenue(venueId);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Markers retrieved successfully", response, Instant.now()));
    }

}
