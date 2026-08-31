package com.bookfair.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.common.LayoutPositionDto;
import com.bookfair.backend.dto.layout.request.GenerateStallGridRequest;
import com.bookfair.backend.dto.stall.mapper.StallMapper;
import com.bookfair.backend.dto.stall.response.StallResponse;
import com.bookfair.backend.model.Stall;
import com.bookfair.backend.service.LayoutGenerationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/layout")
@RequiredArgsConstructor
public class LayoutController {

    private final LayoutGenerationService layoutGenerationService;
    private final StallMapper stallMapper;

    @GetMapping("/hall/{hallId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDto<List<StallResponse>>> getHallLayout(@PathVariable UUID hallId) {
        List<StallResponse> response = layoutGenerationService.getHallLayout(hallId);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Hall layout fetched successfully", response, Instant.now()));
    }

    @PostMapping("/hall/{hallId}/generate")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdminByHall(authentication, #hallId)")
    public ResponseEntity<ApiResponseDto<List<StallResponse>>> autoGenerateStallGrid(
            @PathVariable UUID hallId, 
            @Valid @RequestBody GenerateStallGridRequest request) {
        List<Stall> stalls = layoutGenerationService.autoGenerateStallGrid(
                hallId, 
                request.rows(), 
                request.columns(), 
                request.stallWidth(), 
                request.stallLength(), 
                request.aisleWidth(), 
                request.startX(), 
                request.startY()
        );
        List<StallResponse> response = stalls.stream().map(stallMapper::toStallResponse).collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDto<>(true, "Stall grid generated successfully", response, Instant.now()));
    }

    @PutMapping("/stall/{stallId}/coordinates")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdminByStall(authentication, #stallId)")
    public ResponseEntity<ApiResponseDto<StallResponse>> updateStallCoordinates(
            @PathVariable UUID stallId, 
            @Valid @RequestBody LayoutPositionDto request) {
        Stall updatedStall = layoutGenerationService.updateStallCoordinates(stallId, request);
        StallResponse data = stallMapper.toStallResponse(updatedStall);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Stall coordinates updated successfully", data, Instant.now()));
    }
}
