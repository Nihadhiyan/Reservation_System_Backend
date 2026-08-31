package com.bookfair.backend.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookfair.backend.dto.admin.response.AdminDashboardResponse;
import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.service.AdminService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponseDto<AdminDashboardResponse>> getDashboardMetrics() {
        AdminDashboardResponse data = adminService.getDashboardStats();
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Dashboard metrics fetched successfully", data, Instant.now()));
    }

    @PreAuthorize("hasRole('ORG_ADMIN')")
    @GetMapping("/dashboard/org")
    public ResponseEntity<ApiResponseDto<AdminDashboardResponse>> getOrgDashboardMetrics(org.springframework.security.core.Authentication authentication) {
        AdminDashboardResponse data = adminService.getOrgDashboardStats(UUID.fromString(authentication.getName()));
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Org dashboard metrics fetched successfully", data, Instant.now()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/system/maintenance")
    public ResponseEntity<ApiResponseDto<String>> toggleMaintenanceMode() {
        boolean newMode = adminService.toggleMaintenanceMode();
        String status = "Maintenance mode is now: " + newMode;
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Maintenance mode toggled successfully", status, Instant.now()));
    }

    @GetMapping("/system/maintenance")
    public ResponseEntity<ApiResponseDto<Boolean>> getMaintenanceMode() {
        return ResponseEntity.ok(new ApiResponseDto<>(
                true, "Maintenance mode status", adminService.isMaintenanceMode(), Instant.now()));
    }
}
