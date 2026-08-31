package com.bookfair.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.ReservationStatus;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.repository.query.Param;

import com.bookfair.backend.model.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    
    List<Reservation> findByUserId(UUID userId);
    
    List<Reservation> findByOrganizationIdAndStatus(UUID organizationId, ReservationStatus status);
    
    List<Reservation> findByUserIdAndStatus(UUID userId, ReservationStatus status);

    List<Reservation> findByUserOrderByCreatedAtDesc(User user);
    
    List<Reservation> findByStatus(ReservationStatus status);

    Optional<Reservation> findByIdAndStatus(UUID reservationId, ReservationStatus status);

    List<Reservation> findByEventId(UUID eventId);

    // Avoids one query per stall when checking a whole
    // Venue/Building/Floor/Hall-level booking's stalls for active vendor reservations.
    @Query("SELECT DISTINCT r FROM Reservation r JOIN r.spaceBookings b "
            + "WHERE b.event.id = :eventId AND b.stall.id IN :stallIds AND r.status IN :statuses")
    List<Reservation> findByEventIdAndStallIdInAndStatusIn(@Param("eventId") UUID eventId,
            @Param("stallIds") List<UUID> stallIds, @Param("statuses") List<ReservationStatus> statuses);

    Page<Reservation> findByOrganizationId(UUID organizationId, Pageable pageable);

    List<Reservation> findByExpiresAtBeforeAndStatus(Instant expiresAt, ReservationStatus status);

    long countByExpiresAtAfterAndStatus(Instant date, ReservationStatus status);

    @Query("SELECT COALESCE(SUM(r.totalPrice), 0) FROM Reservation r WHERE r.status = :status")
    BigDecimal sumTotalPriceByStatus(@Param("status") ReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT r FROM Reservation r WHERE r.id = :id AND r.status = :status")
    Optional<Reservation> findByIdAndStatusForUpdate(@Param("id") UUID id, @Param("status") ReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);
}
