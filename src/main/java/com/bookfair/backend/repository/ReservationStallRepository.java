package com.bookfair.backend.repository;

import com.bookfair.backend.model.ReservationStall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationStallRepository extends JpaRepository<ReservationStall, UUID> {

    // All reservation stalls for a reservation
    List<ReservationStall> findByReservationId(UUID reservationId);

    // Check if a specific event stall is already reserved
    // Used for double-booking prevention check (secondary to pessimistic lock)
    boolean existsByEventStallId(UUID eventStallId);

    // Check if event stall is reserved by a specific reservation
    boolean existsByReservationIdAndEventStallId(UUID reservationId, UUID eventStallId);

    // All event stalls reserved within a specific event
    @Query("""
        SELECT rs FROM ReservationStall rs
        JOIN FETCH rs.eventStall es
        JOIN FETCH es.stall s
        WHERE es.event.id = :eventId
        """)
    List<ReservationStall> findByEventId(@Param("eventId") UUID eventId);

    // Delete all reservation stalls for a reservation
    // Used when reservation is cancelled
    @Transactional
    @Modifying
    @Query("""
        DELETE FROM ReservationStall rs
        WHERE rs.reservation.id = :reservationId
        """)
    int deleteByReservationId(@Param("reservationId") UUID reservationId);
}
