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
import com.bookfair.backend.dto.hall.request.CreateHallRequest;
import com.bookfair.backend.dto.hall.request.UpdateHallRequest;
import com.bookfair.backend.dto.hall.response.HallLayoutResponse;
import com.bookfair.backend.dto.hall.response.HallResponse;
import com.bookfair.backend.dto.stall.response.StallResponse;
import com.bookfair.backend.service.HallService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/halls")
@RequiredArgsConstructor
public class HallController {

    private final HallService hallService;

    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdminByFloor(authentication, #request.floorId())")
    @PostMapping
    public org.springframework.http.ResponseEntity<ApiResponseDto<HallResponse>> createHall(@Valid @RequestBody CreateHallRequest request) {
        HallResponse data = hallService.createHall(request);
        return org.springframework.http.ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDto<>(true, "Hall created successfully", data, Instant.now()));
    }

    @GetMapping("/{id}")
    public org.springframework.http.ResponseEntity<ApiResponseDto<HallResponse>> getHallById(@PathVariable UUID id) {
        HallResponse data = hallService.getHallById(id);
        return org.springframework.http.ResponseEntity.ok(new ApiResponseDto<>(true, "Hall fetched successfully", data, Instant.now()));
    }

    @GetMapping("/{id}/layout")
    public org.springframework.http.ResponseEntity<ApiResponseDto<HallLayoutResponse>> getHallLayout(@PathVariable UUID id) {
        HallLayoutResponse data = hallService.getHallLayout(id);
        return org.springframework.http.ResponseEntity.ok(new ApiResponseDto<>(true, "Hall layout fetched successfully", data, Instant.now()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdminByHall(authentication, #id)")
    @PutMapping("/{id}")
    public org.springframework.http.ResponseEntity<ApiResponseDto<HallResponse>> updateHall(@PathVariable UUID id, @Valid @RequestBody UpdateHallRequest request) {
        HallResponse data = hallService.updateHall(id, request);
        return org.springframework.http.ResponseEntity.ok(new ApiResponseDto<>(true, "Hall updated successfully", data, Instant.now()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdminByHall(authentication, #id)")
    @DeleteMapping("/{id}")
    public org.springframework.http.ResponseEntity<ApiResponseDto<Void>> deleteHall(@PathVariable UUID id) {
        hallService.deleteHall(id);
        return org.springframework.http.ResponseEntity.ok(new ApiResponseDto<>(true, "Hall deactivated successfully", null, Instant.now()));
    }

    @GetMapping("/{id}/stalls")
    public org.springframework.http.ResponseEntity<ApiResponseDto<List<StallResponse>>> getStallsByHall(@PathVariable UUID id) {
        List<StallResponse> data = hallService.getStallsByHall(id);
        return org.springframework.http.ResponseEntity.ok(new ApiResponseDto<>(true, "Stalls fetched successfully", data, Instant.now()));
    }

}
