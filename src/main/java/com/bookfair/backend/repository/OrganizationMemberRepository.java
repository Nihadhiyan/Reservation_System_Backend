package com.bookfair.backend.repository;

import com.bookfair.backend.model.OrganizationMember;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bookfair.backend.model.enums.OrganizationRole;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {
    Optional<OrganizationMember> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    Optional<OrganizationMember> findByUserId(UUID userId);

    List<OrganizationMember> findByOrganizationId(UUID organizationId);

    boolean existsByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    List<OrganizationMember> findByOrganizationIdAndActiveTrue(UUID organizationId);

    List<OrganizationMember> findByOrganizationIdAndRole(UUID organizationId, OrganizationRole role);

    @Query("SELECT om FROM OrganizationMember om JOIN FETCH om.organization WHERE om.user.id = :userId AND om.active = true")
    List<OrganizationMember> findByUserIdWithOrganizations(@Param("userId") UUID userId);

    List<OrganizationMember> findAllByOrganizationIdAndActiveTrue(UUID organizationId);

    long countByOrganizationId(UUID id);

    boolean existsByUserIdAndRoleAndActiveTrue(UUID userId, OrganizationRole role);
}
