package com.bookfair.backend.service;

import com.bookfair.backend.dto.admin.mapper.AdminMapper;
import com.bookfair.backend.dto.admin.response.AdminDashboardResponse;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.OrganizationMember;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.model.enums.ReservationStatus;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.repository.StallRepository;
import com.bookfair.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.bookfair.backend.event.audit.SecurityAuditEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.exception.ForbiddenException;
import com.bookfair.backend.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final StallRepository stallRepository;
    private final ReservationRepository reservationRepository;
    private final AdminMapper adminMapper;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    private static final String MAINTENANCE_MODE_KEY = "system:maintenance_mode";

    // Read-only transaction for real-time dashboard metrics calculation
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStats() {
        long totalUsers = userRepository.countByActiveTrue();
        long totalStalls = stallRepository.countByActiveTrue();
        long activeReservations = reservationRepository
                .countByExpiresAtAfterAndStatus(Instant.now(), ReservationStatus.CONFIRMED);

        // Null protection for JPQL SUM() aggregation
        BigDecimal totalRevenue = Optional.ofNullable(
                reservationRepository.sumTotalPriceByStatus(ReservationStatus.CONFIRMED)).orElse(BigDecimal.ZERO);

        return adminMapper.toAdminDashboardResponse(
                totalUsers, totalStalls, activeReservations, totalRevenue);
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getOrgDashboardStats(UUID userId) {
        OrganizationMember member = organizationMemberRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization membership not found", ErrorCode.ORGANIZATION_MEMBERSHIP_NOT_FOUND));

        if (!member.getRole().equals(OrganizationRole.ORG_ADMIN)) {
            throw new ForbiddenException("Unauthorized", ErrorCode.FORBIDDEN);
        }

        Organization organization = member.getOrganization();

        long totalmembers = organizationMemberRepository.countByOrganizationId(organization.getId());
        
        List<Reservation> activeReservationsList = reservationRepository.findByOrganizationIdAndStatus(organization.getId(), ReservationStatus.CONFIRMED);
        
        long totalStalls = activeReservationsList.stream()
                .filter(r -> r.getSpaceBookings() != null)
                .mapToLong(r -> r.getSpaceBookings().size())
                .sum();
                
        long activeReservationsCount = activeReservationsList.size();
        
        BigDecimal totalRevenue = activeReservationsList.stream()
                .map(Reservation::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AdminDashboardResponse(
                totalmembers, totalStalls, activeReservationsCount, totalRevenue);
    }

    // Service-level security enforcement
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public boolean toggleMaintenanceMode() {
        // Thread-safe state toggle
        Boolean current = Boolean.parseBoolean(redisTemplate.opsForValue().get(MAINTENANCE_MODE_KEY));
        boolean currentMode = Boolean.TRUE.equals(current);
        boolean newMode = !currentMode;
        redisTemplate.opsForValue().set(MAINTENANCE_MODE_KEY, String.valueOf(newMode));

        String performedBy = SecurityContextHolder.getContext()
            .getAuthentication().getName();

        eventPublisher.publishEvent(new SecurityAuditEvent(
            "TOGGLE_MAINTENANCE_MODE",
            performedBy,
            "Maintenance mode toggled to: " + newMode,
            Instant.now()
        ));

        log.info("Maintenance mode toggled to {} by {}", newMode, performedBy);
        return newMode;
    }

    public boolean isMaintenanceMode() {
        return Boolean.TRUE.equals(
                Boolean.parseBoolean(redisTemplate.opsForValue().get(MAINTENANCE_MODE_KEY)));
    }
}