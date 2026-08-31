package com.bookfair.backend.service;

import com.bookfair.backend.dto.admin.mapper.AdminMapper;
import com.bookfair.backend.event.audit.SecurityAuditEvent;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ForbiddenException;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.OrganizationMember;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.repository.StallRepository;
import com.bookfair.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers two regressions fixed during the review:
 *  - toggleMaintenanceMode previously published a duplicate SecurityAuditEvent
 *    carrying a stale, never-updated AtomicBoolean (always "false").
 *  - getOrgDashboardStats previously threw ORGANIZATION_NOT_FOUND when the
 *    actual missing thing was the caller's membership record.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private StallRepository stallRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AdminMapper adminMapper;
    @Mock private OrganizationMemberRepository organizationMemberRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("super-admin-1", "pw", "ROLE_SUPER_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void toggleMaintenanceMode_publishesExactlyOneAuditEvent_withCorrectData() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("system:maintenance_mode")).thenReturn("false");

        boolean result = adminService.toggleMaintenanceMode();

        assertThat(result).isTrue();
        verify(valueOperations).set("system:maintenance_mode", "true");

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        SecurityAuditEvent published = captor.getValue();
        assertThat(published.performedBy()).isEqualTo("super-admin-1");
        assertThat(published.details()).contains("true");
    }

    @Test
    void toggleMaintenanceMode_flipsBackToFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("system:maintenance_mode")).thenReturn("true");

        boolean result = adminService.toggleMaintenanceMode();

        assertThat(result).isFalse();
        verify(valueOperations).set("system:maintenance_mode", "false");
    }

    @Test
    void isMaintenanceMode_reflectsStoredValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("system:maintenance_mode")).thenReturn("true");

        assertThat(adminService.isMaintenanceMode()).isTrue();
    }

    @Test
    void getOrgDashboardStats_throwsMembershipNotFound_notOrganizationNotFound() {
        UUID userId = UUID.randomUUID();
        when(organizationMemberRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getOrgDashboardStats(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ORGANIZATION_MEMBERSHIP_NOT_FOUND));
    }

    @Test
    void getOrgDashboardStats_forbidsNonAdminMember() {
        UUID userId = UUID.randomUUID();
        OrganizationMember member = new OrganizationMember();
        member.setRole(OrganizationRole.ORG_MEMBER);
        when(organizationMemberRepository.findByUserId(userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> adminService.getOrgDashboardStats(userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getOrgDashboardStats_returnsAggregatedStats_forOrgAdmin() {
        UUID userId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        OrganizationMember member = new OrganizationMember();
        member.setRole(OrganizationRole.ORG_ADMIN);
        member.setOrganization(org);

        when(organizationMemberRepository.findByUserId(userId)).thenReturn(Optional.of(member));
        when(organizationMemberRepository.countByOrganizationId(org.getId())).thenReturn(4L);
        when(reservationRepository.findByOrganizationIdAndStatus(eq(org.getId()), any())).thenReturn(List.of());

        var stats = adminService.getOrgDashboardStats(userId);

        assertThat(stats).isNotNull();
    }
}
