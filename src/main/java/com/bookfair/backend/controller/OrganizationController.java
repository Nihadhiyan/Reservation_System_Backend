package com.bookfair.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.organization.request.CreateOrganizationRequest;
import com.bookfair.backend.dto.organization.request.UpdateOrganizationRequest;
import com.bookfair.backend.dto.organization.response.OrganizationMemberResponse;
import com.bookfair.backend.dto.organization.response.OrganizationResponse;
import com.bookfair.backend.dto.organization.response.PublicOrganizationResponse;
import com.bookfair.backend.service.OrganizationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponseDto<OrganizationResponse>> createOrganization(
            @RequestBody @Valid CreateOrganizationRequest request) {
        OrganizationResponse response = organizationService.createOrganization(request);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Organization created successfully", response, Instant.now()));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<Page<PublicOrganizationResponse>>> getAllOrganizations(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<PublicOrganizationResponse> response = organizationService.getAllOrganizations(pageable);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Organizations retrieved successfully", response, Instant.now()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<List<OrganizationResponse>>> getMyOrganizations(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        List<OrganizationResponse> response = organizationService.getMyOrganizations(userId);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Your organizations retrieved successfully", response, Instant.now()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<PublicOrganizationResponse>> getOrganization(@PathVariable UUID id) {
        PublicOrganizationResponse response = organizationService.getOrganizationById(id);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Organization retrieved successfully", response, Instant.now()));
    }

    @PreAuthorize("@orgAuth.isOrgAdmin(authentication, #id)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<OrganizationResponse>> updateOrganization(@PathVariable UUID id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        OrganizationResponse response = organizationService.updateOrganization(id, request);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Organization updated successfully", response, Instant.now()));
    }

    @PreAuthorize("@orgAuth.isOrgAdmin(authentication, #id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> deleteOrganization(@PathVariable UUID id) {
        organizationService.deactivateOrganization(id);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Organization deactivated successfully", null, Instant.now()));
    }

    @PreAuthorize("@orgAuth.isMemberOf(authentication, #id)")
    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponseDto<List<OrganizationMemberResponse>>> getMembers(@PathVariable UUID id) {
        List<OrganizationMemberResponse> response = organizationService.getMembers(id);
        return ResponseEntity
                .ok(new ApiResponseDto<>(true, "Members retrieved successfully", response, Instant.now()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{id}/verify")
    public ResponseEntity<ApiResponseDto<Void>> verifyOrganization(@PathVariable UUID id) {
        organizationService.verifyOrganization(id);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Organization verified successfully", null, Instant.now()));
    }
}
