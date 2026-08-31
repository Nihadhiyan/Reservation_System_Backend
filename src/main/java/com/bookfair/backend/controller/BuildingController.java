package com.bookfair.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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

import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.building.request.CreateBuildingRequest;
import com.bookfair.backend.dto.building.request.UpdateBuildingRequest;
import com.bookfair.backend.dto.building.response.BuildingResponse;
import com.bookfair.backend.dto.floor.response.FloorResponse;
import com.bookfair.backend.service.BuildingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/buildings")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdminByVenue(authentication, #request.venueId())")
    @PostMapping
    public ResponseEntity<ApiResponseDto<BuildingResponse>> createBuilding(@Valid @RequestBody CreateBuildingRequest request) {
        BuildingResponse data = buildingService.createBuilding(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDto<>(true, "Building created successfully", data, Instant.now()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<BuildingResponse>> getBuildingById(@PathVariable UUID id) {
        BuildingResponse data = buildingService.getBuildingById(id);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Building fetched successfully", data, Instant.now()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdminByBuilding(authentication, #id)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<BuildingResponse>> updateBuilding(@PathVariable UUID id, @Valid @RequestBody UpdateBuildingRequest request) {
        BuildingResponse data = buildingService.updateBuilding(id, request);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Building updated successfully", data, Instant.now()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdminByBuilding(authentication, #id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> deleteBuilding(@PathVariable UUID id) {
        buildingService.deleteBuilding(id);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Building deactivated successfully", null, Instant.now()));
    }

    @GetMapping("/{id}/floors")
    public ResponseEntity<ApiResponseDto<List<FloorResponse>>> getFloorsByBuilding(@PathVariable UUID id) {
        List<FloorResponse> data = buildingService.getFloorsByBuilding(id);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Floors fetched successfully", data, Instant.now()));
    }
}
