package com.bookfair.backend.service;

import com.bookfair.backend.dto.organization.mapper.OrganizationMapper;
import com.bookfair.backend.dto.organization.request.InviteRequest;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.OrganizationInvite;
import com.bookfair.backend.model.User;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.repository.*;
import com.bookfair.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock private OrganizationInviteRepository inviteRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMapper organizationMapper;
    @Mock private JwtService jwtService;
    @Mock private TokenManagementService tokenManagementService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private InviteService inviteService;

    private UUID orgId;
    private Organization org;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        org = new Organization();
        org.setId(orgId);
        org.setName("Acme Books");
        org.setActive(true);
    }

    // ---------- inviteUser ----------

    @Test
    void inviteUser_rejectsWhenActiveInviteAlreadyExists() {
        InviteRequest request = new InviteRequest(orgId, "vendor@example.com", OrganizationRole.ORG_MEMBER, false);
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(org));
        when(inviteRepository.existsByEmailAndOrganizationIdAndUsedFalseAndExpiresAtAfter(
                eq("vendor@example.com"), eq(orgId), any())).thenReturn(true);

        assertThatThrownBy(() -> inviteService.inviteUser(request))
                .isInstanceOf(BusinessException.class);

        verify(jwtService, never()).generateInviteToken(any());
    }

    @Test
    void inviteUser_rejectsWhenEmailAlreadyBelongsToAMember() {
        InviteRequest request = new InviteRequest(orgId, "vendor@example.com", OrganizationRole.ORG_MEMBER, false);
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(org));
        when(inviteRepository.existsByEmailAndOrganizationIdAndUsedFalseAndExpiresAtAfter(any(), any(), any()))
                .thenReturn(false);

        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        when(userRepository.findByEmailAndActiveTrue("vendor@example.com")).thenReturn(Optional.of(existingUser));
        when(memberRepository.existsByUserIdAndOrganizationId(existingUser.getId(), orgId)).thenReturn(true);

        assertThatThrownBy(() -> inviteService.inviteUser(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void inviteUser_rejectsForInactiveOrganization() {
        InviteRequest request = new InviteRequest(orgId, "vendor@example.com", OrganizationRole.ORG_MEMBER, false);
        // Regression test: inviteUser must use findByIdAndActiveTrue, not findById.
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inviteService.inviteUser(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inviteUser_sendsInvite_whenNoConflicts() {
        InviteRequest request = new InviteRequest(orgId, "vendor@example.com", OrganizationRole.ORG_MEMBER, false);
        when(organizationRepository.findByIdAndActiveTrue(orgId)).thenReturn(Optional.of(org));
        when(inviteRepository.existsByEmailAndOrganizationIdAndUsedFalseAndExpiresAtAfter(any(), any(), any()))
                .thenReturn(false);
        when(userRepository.findByEmailAndActiveTrue("vendor@example.com")).thenReturn(Optional.empty());
        when(jwtService.generateInviteToken("vendor@example.com")).thenReturn("jwt-token");
        when(jwtService.extractJti("jwt-token")).thenReturn("jti-123");
        OrganizationInvite invite = new OrganizationInvite();
        when(organizationMapper.toOrganizationInvite(eq(org), eq(request), eq("jti-123"), any())).thenReturn(invite);

        inviteService.inviteUser(request);

        verify(inviteRepository).save(invite);
        verify(tokenManagementService).storeInviteToken("vendor@example.com", "jti-123", 7, TimeUnit.DAYS);
        verify(eventPublisher).publishEvent(any(com.bookfair.backend.event.organization.OrganizationInviteSentEvent.class));
    }

    // ---------- acceptInvite ----------

    @Test
    void acceptInvite_rejectsExpiredToken() {
        when(jwtService.isTokenExpired("expired-token")).thenReturn(true);

        assertThatThrownBy(() -> inviteService.acceptInvite("expired-token", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptInvite_rejectsWrongEmail() {
        UUID userId = UUID.randomUUID();
        when(jwtService.isTokenExpired("tok")).thenReturn(false);
        when(jwtService.extractPurpose("tok")).thenReturn("ORG_INVITE");
        when(jwtService.extractJti("tok")).thenReturn("jti-1");
        when(jwtService.extractSubject("tok")).thenReturn("invited@example.com");

        OrganizationInvite invite = new OrganizationInvite();
        invite.setUsed(false);
        invite.setOrganization(org);
        when(inviteRepository.findByToken("jti-1")).thenReturn(Optional.of(invite));

        User user = new User();
        user.setId(userId);
        user.setEmail("someone-else@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> inviteService.acceptInvite("tok", userId))
                .isInstanceOf(BusinessException.class);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void acceptInvite_rejectsAlreadyUsedInvite() {
        when(jwtService.isTokenExpired("tok")).thenReturn(false);
        when(jwtService.extractPurpose("tok")).thenReturn("ORG_INVITE");
        when(jwtService.extractJti("tok")).thenReturn("jti-1");

        OrganizationInvite invite = new OrganizationInvite();
        invite.setUsed(true);
        when(inviteRepository.findByToken("jti-1")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> inviteService.acceptInvite("tok", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptInvite_rejectsWhenOrganizationDeactivatedSinceInviteSent() {
        UUID userId = UUID.randomUUID();
        when(jwtService.isTokenExpired("tok")).thenReturn(false);
        when(jwtService.extractPurpose("tok")).thenReturn("ORG_INVITE");
        when(jwtService.extractJti("tok")).thenReturn("jti-1");
        when(jwtService.extractSubject("tok")).thenReturn("vendor@example.com");

        org.setActive(false);
        OrganizationInvite invite = new OrganizationInvite();
        invite.setUsed(false);
        invite.setOrganization(org);
        when(inviteRepository.findByToken("jti-1")).thenReturn(Optional.of(invite));

        User user = new User();
        user.setId(userId);
        user.setEmail("vendor@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> inviteService.acceptInvite("tok", userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptInvite_succeeds_whenTokenValidAndUnused() {
        UUID userId = UUID.randomUUID();
        when(jwtService.isTokenExpired("tok")).thenReturn(false);
        when(jwtService.extractPurpose("tok")).thenReturn("ORG_INVITE");
        when(jwtService.extractJti("tok")).thenReturn("jti-1");
        when(jwtService.extractSubject("tok")).thenReturn("vendor@example.com");

        OrganizationInvite invite = new OrganizationInvite();
        invite.setUsed(false);
        invite.setOrganization(org);
        invite.setAssignedRole(OrganizationRole.ORG_MEMBER);
        when(inviteRepository.findByToken("jti-1")).thenReturn(Optional.of(invite));

        User user = new User();
        user.setId(userId);
        user.setUsername("vendor1");
        user.setEmail("vendor@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(memberRepository.existsByUserIdAndOrganizationId(userId, orgId)).thenReturn(false);
        when(tokenManagementService.consumeInviteToken("jti-1", "vendor@example.com")).thenReturn(true);
        when(organizationMapper.toOrganizationMember(eq(user), eq(org), eq(OrganizationRole.ORG_MEMBER)))
                .thenReturn(new com.bookfair.backend.model.OrganizationMember());

        inviteService.acceptInvite("tok", userId);

        assertThat(invite.getUsed()).isTrue();
        verify(memberRepository).save(any());
        verify(tokenBlacklistService).createSecurityCheckpoint(userId);
        verify(eventPublisher).publishEvent(any(com.bookfair.backend.event.organization.UserJoinedOrganizationEvent.class));
    }

    @Test
    void acceptInvite_rejectsWhenTokenConsumptionFails() {
        // Redis-backed one-time-consumption check fails closed — if it says no, don't proceed.
        UUID userId = UUID.randomUUID();
        when(jwtService.isTokenExpired("tok")).thenReturn(false);
        when(jwtService.extractPurpose("tok")).thenReturn("ORG_INVITE");
        when(jwtService.extractJti("tok")).thenReturn("jti-1");
        when(jwtService.extractSubject("tok")).thenReturn("vendor@example.com");

        OrganizationInvite invite = new OrganizationInvite();
        invite.setUsed(false);
        invite.setOrganization(org);
        when(inviteRepository.findByToken("jti-1")).thenReturn(Optional.of(invite));

        User user = new User();
        user.setId(userId);
        user.setEmail("vendor@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(memberRepository.existsByUserIdAndOrganizationId(userId, orgId)).thenReturn(false);
        when(tokenManagementService.consumeInviteToken("jti-1", "vendor@example.com")).thenReturn(false);

        assertThatThrownBy(() -> inviteService.acceptInvite("tok", userId))
                .isInstanceOf(BusinessException.class);

        verify(memberRepository, never()).save(any());
    }

}
