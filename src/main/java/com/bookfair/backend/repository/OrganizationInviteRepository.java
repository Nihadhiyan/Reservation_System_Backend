package com.bookfair.backend.repository;

import com.bookfair.backend.model.OrganizationInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, UUID> {
    Optional<OrganizationInvite> findByToken(String token);
    boolean existsByEmailAndOrganizationIdAndUsedFalseAndExpiresAtAfter(String email, UUID organizationId, java.time.Instant now);
}
