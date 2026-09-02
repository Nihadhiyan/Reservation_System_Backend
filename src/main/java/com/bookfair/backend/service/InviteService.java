package com.bookfair.backend.service;

import com.bookfair.backend.dto.organization.mapper.OrganizationMapper;
import com.bookfair.backend.dto.organization.request.InviteRequest;
import com.bookfair.backend.event.organization.OrganizationInviteSentEvent;
import com.bookfair.backend.event.organization.UserJoinedOrganizationEvent;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.*;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.repository.*;
import com.bookfair.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class InviteService {

    private final OrganizationInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;
    private final JwtService jwtService;
    private final TokenManagementService tokenManagementService;
    private final ApplicationEventPublisher eventPublisher;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public void inviteUser(InviteRequest request) {
        Objects.requireNonNull(request, "Request must not be null");
        Objects.requireNonNull(request.orgId(), "Organization ID must not be null");
        Objects.requireNonNull(request.email(), "Email must not be null");
        Objects.requireNonNull(request.role(), "Role must not be null");

        Organization org = organizationRepository.findByIdAndActiveTrue(Objects.requireNonNull(request.orgId()))
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found",
                        ErrorCode.ORGANIZATION_NOT_FOUND));

        boolean activeInviteExists = inviteRepository
                .existsByEmailAndOrganizationIdAndUsedFalseAndExpiresAtAfter(
                        request.email(), org.getId(), Instant.now());

        if (activeInviteExists) {
            throw new BusinessException(
                    "An active invite already exists for this email address.",
                    ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        userRepository.findByEmailAndActiveTrue(request.email()).ifPresent(existingUser -> {
            if (memberRepository.existsByUserIdAndOrganizationId(existingUser.getId(), org.getId())) {
                throw new BusinessException(
                        "This user is already a member of the organization.",
                        ErrorCode.BUSINESS_RULE_VIOLATION);
            }

            // Someone who already runs another organization is unusual enough to
            // be worth a deliberate choice, not a silent invite — regardless of
            // which role they're being offered here. Surface it once and let
            // the inviter decide, rather than blocking it outright (there's no
            // rule against it, just enough ambiguity that it shouldn't happen
            // by accident).
            boolean alreadyOrgAdminElsewhere = memberRepository
                    .existsByUserIdAndRoleAndActiveTrue(existingUser.getId(), OrganizationRole.ORG_ADMIN);

            if (alreadyOrgAdminElsewhere && !Boolean.TRUE.equals(request.confirmed())) {
                throw new BusinessException(
                        "This user is already an Organization Admin in another organization. "
                                + "Send the invite anyway?",
                        ErrorCode.CONFIRMATION_REQUIRED);
            }
        });

        // Generate JWT invite token
        String tokenString = jwtService.generateInviteToken(request.email());
        String jti = jwtService.extractJti(tokenString);
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        OrganizationInvite invite = organizationMapper.toOrganizationInvite(org, request, jti, expiresAt);

        inviteRepository.save(Objects.requireNonNull(invite));
        
        tokenManagementService.storeInviteToken(request.email(), jti, 7, TimeUnit.DAYS);

        // Send email via async event
        String acceptLink = "https://frontend-url/accept-invite?token=" + tokenString;
        eventPublisher.publishEvent(
                new OrganizationInviteSentEvent(
                        org.getId(),
                        org.getName(),
                        request.email(),
                        request.role(),
                        acceptLink));
    }

    @Transactional
    public void acceptInvite(String tokenString, UUID userId) {
        Objects.requireNonNull(tokenString, "Token must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        if (jwtService.isTokenExpired(tokenString)) {
            throw new BusinessException("Invite has expired", ErrorCode.TOKEN_EXPIRED);
        }

        String purpose = jwtService.extractPurpose(tokenString);
        if (!"ORG_INVITE".equals(purpose)) {
            throw new BusinessException("Invalid token", ErrorCode.UNAUTHORIZED);
        }

        String jti = jwtService.extractJti(tokenString);
        String inviteEmail = jwtService.extractSubject(tokenString);

        OrganizationInvite invite = inviteRepository.findByToken(jti)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Invite not found", ErrorCode.INVITE_NOT_FOUND));

        if (invite.getUsed()) {
            throw new BusinessException("Invite has already been used", ErrorCode.INVITATION_USED);
        }

        User user = userRepository.findById(Objects.requireNonNull(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.USER_NOT_FOUND));

        if (!user.getEmail().equalsIgnoreCase(inviteEmail)) {
            throw new BusinessException("This invite was sent to a different email address",
                    ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        Organization org = invite.getOrganization();
        if (org == null || !Boolean.TRUE.equals(org.getActive())) {
            throw new BusinessException("The organization you were invited to is no longer active.", ErrorCode.ORGANIZATION_NOT_FOUND);
        }

        // Check if member already exists
        if (memberRepository.existsByUserIdAndOrganizationId(Objects.requireNonNull(user.getId()),
                Objects.requireNonNull(org.getId()))) {
            throw new BusinessException("User is already a member of this organization",
                    ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        if (!tokenManagementService.consumeInviteToken(jti, inviteEmail)) {
            throw new BusinessException("Token is invalid, expired, or has already been used.", ErrorCode.UNAUTHORIZED);
        }

        OrganizationMember member = organizationMapper.toOrganizationMember(user, org, invite.getAssignedRole());

        memberRepository.save(Objects.requireNonNull(member));

        invite.setUsed(true);
        inviteRepository.save(Objects.requireNonNull(invite));

        // Issue checkpoint to force user to get a fresh token with new org permissions
        tokenBlacklistService.createSecurityCheckpoint(user.getId());

        // Notify admins and log audit event
        eventPublisher.publishEvent(new UserJoinedOrganizationEvent(
                org.getId(),
                org.getName(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                invite.getAssignedRole()));
    }
}
