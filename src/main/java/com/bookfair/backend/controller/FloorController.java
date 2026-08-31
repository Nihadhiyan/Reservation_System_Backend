package com.bookfair.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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
import com.bookfair.backend.dto.floor.request.CreateFloorRequest;
import com.bookfair.backend.dto.floor.request.UpdateFloorRequest;
import com.bookfair.backend.dto.floor.response.FloorResponse;
import com.bookfair.backend.dto.hall.response.HallResponse;
import com.bookfair.backend.service.FloorService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/floors")
@RequiredArgsConstructor
public class FloorController {

    private final FloorService floorService;

    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdminByBuilding(authentication, #request.buildingId())")
    @PostMapping
    public org.springframework.http.ResponseEntity<ApiResponseDto<FloorResponse>> createFloor(@Valid @RequestBody CreateFloorRequest request) {
        FloorResponse data = floorService.createFloor(request);
        return org.springframework.http.ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDto<>(true, "Floor created successfully", data, Instant.now()));
    }

    @GetMapping("/{id}")
    public org.springframework.http.ResponseEntity<ApiResponseDto<FloorResponse>> getFloorById(@PathVariable UUID id) {
        FloorResponse data = floorService.getFloorById(id);
        return org.springframework.http.ResponseEntity.ok(new ApiResponseDto<>(true, "Floor fetched successfully", data, Instant.now()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdminByFloor(authentication, #id)")
    @PutMapping("/{id}")
    public org.springframework.http.ResponseEntity<ApiResponseDto<FloorResponse>> updateFloor(@PathVariable UUID id, @Valid @RequestBody UpdateFloorRequest request) {
        FloorResponse data = floorService.updateFloor(id, request);
        return org.springframework.http.ResponseEntity.ok(new ApiResponseDto<>(true, "Floor updated successfully", data, Instant.now()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdminByFloor(authentication, #id)")
    @DeleteMapping("/{id}")
    public org.springframework.http.ResponseEntity<ApiResponseDto<Void>> deleteFloor(@PathVariable UUID id) {
        floorService.deleteFloor(id);
        return org.springframework.http.ResponseEntity.ok(new ApiResponseDto<>(true, "Floor deactivated successfully", null, Instant.now()));
    }

    @GetMapping("/{id}/halls")
    public org.springframework.http.ResponseEntity<ApiResponseDto<List<HallResponse>>> getHallsByFloor(@PathVariable UUID id) {
        List<HallResponse> data = floorService.getHallsByFloor(id);
        return org.springframework.http.ResponseEntity.ok(new ApiResponseDto<>(true, "Halls fetched successfully", data, Instant.now()));
    }
}
