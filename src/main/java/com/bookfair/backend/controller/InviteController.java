package com.bookfair.backend.controller;

import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.organization.request.InviteRequest;
import com.bookfair.backend.service.InviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organizations/invites")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @PostMapping
    @PreAuthorize("@orgAuth.isOrgAdmin(authentication, #request.orgId())")
    public ResponseEntity<ApiResponseDto<Void>> sendInvite(
            @Valid @RequestBody InviteRequest request) {

        inviteService.inviteUser(request);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Invite sent successfully", null, Instant.now()));
    }

    @PostMapping("/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDto<Void>> acceptInvite(
            @RequestParam String token,
            @AuthenticationPrincipal UUID userId) {
        inviteService.acceptInvite(token, userId);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Invite accepted successfully", null, Instant.now()));
    }
}
