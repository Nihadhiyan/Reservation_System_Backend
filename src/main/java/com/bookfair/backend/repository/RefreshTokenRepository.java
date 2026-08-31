package com.bookfair.backend.repository;

import com.bookfair.backend.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByJti(String jti);

    @Transactional
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") String familyId);

    @Transactional
    @Modifying
    int deleteByExpiryDateBefore(Instant now);

    @Transactional
    @Modifying
    int deleteByRevokedTrueAndCreatedAtBefore(Instant cutoff);
}
