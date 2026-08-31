package com.bookfair.backend.repository;

import com.bookfair.backend.model.EventStall;
import com.bookfair.backend.model.enums.AvailabilityStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventStallRepository extends JpaRepository<EventStall, UUID> {

    // Find one event stall by event and stall combination
    @Query("""
        SELECT es FROM EventStall es
        JOIN FETCH es.stall s
        WHERE es.event.id = :eventId
        AND s.id = :stallId
        """)
    Optional<EventStall> findByEventIdAndStallId(
        @Param("eventId") UUID eventId,
        @Param("stallId") UUID stallId);

    // Bulk lookup — avoids one query per stall when releasing/cancelling a
    // Venue/Building/Floor/Hall-level booking that can span many stalls.
    @Query("""
        SELECT es FROM EventStall es
        JOIN FETCH es.stall s
        WHERE es.event.id = :eventId
        AND s.id IN :stallIds
        """)
    List<EventStall> findByEventIdAndStallIdIn(
        @Param("eventId") UUID eventId,
        @Param("stallIds") List<UUID> stallIds);

    // All active stalls for an event — what vendors see when browsing
    @Query("""
        SELECT es FROM EventStall es
        JOIN FETCH es.stall s
        JOIN FETCH s.hall h
        WHERE es.event.id = :eventId
        AND es.activeForEvent = true
        ORDER BY h.name, s.name
        """)
    List<EventStall> findActiveByEventId(@Param("eventId") UUID eventId);

    // Active stalls for a specific hall within an event
    // Used for the hall layout map view
    @Query("""
        SELECT es FROM EventStall es
        JOIN FETCH es.stall s
        WHERE es.event.id = :eventId
        AND s.hall.id = :hallId
        AND es.activeForEvent = true
        ORDER BY s.name
        """)
    List<EventStall> findByEventIdAndHallIdAndActiveForEventTrue(
        @Param("eventId") UUID eventId,
        @Param("hallId") UUID hallId);

    // All stalls for an event in a hall — including disabled ones
    // For organizer's management view
    @Query("""
        SELECT es FROM EventStall es
        JOIN FETCH es.stall s
        WHERE es.event.id = :eventId
        AND s.hall.id = :hallId
        ORDER BY s.name
        """)
    List<EventStall> findAllByEventIdAndHallId(
        @Param("eventId") UUID eventId,
        @Param("hallId") UUID hallId);

    // Available stalls for vendor booking — active AND available
    @Query("""
        SELECT es FROM EventStall es
        JOIN FETCH es.stall s
        JOIN FETCH s.hall h
        WHERE es.event.id = :eventId
        AND es.activeForEvent = true
        AND es.availabilityStatus = :status
        ORDER BY h.name, s.name
        """)
    List<EventStall> findByEventIdAndAvailabilityStatus(
        @Param("eventId") UUID eventId,
        @Param("status") AvailabilityStatus status);

    // Pessimistic lock for double-booking prevention
    // Used in ReservationService when vendor books stalls
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(
        name = "jakarta.persistence.lock.timeout",
        value = "5000"))
    @Query("""
        SELECT es FROM EventStall es
        WHERE es.id IN :ids
        AND es.activeForEvent = true
        """)
    List<EventStall> findAllForUpdate(@Param("ids") List<UUID> ids);

    // Check if stall already has an event stall for this event
    boolean existsByEventIdAndStallId(UUID eventId, UUID stallId);

    // Bulk existence check — avoids one query per stall when generating EventStall
    // records for a whole booking (e.g. a Venue/Building/Hall-level booking).
    @Query("SELECT es.stall.id FROM EventStall es WHERE es.event.id = :eventId AND es.stall.id IN :stallIds")
    List<UUID> findExistingStallIds(@Param("eventId") UUID eventId, @Param("stallIds") List<UUID> stallIds);

    // Used when reactivating a Hall: a stall with a genuine BOOKED/BLOCKED EventStall
    // must not be silently reactivated — it needs manual review instead.
    boolean existsByStallIdAndAvailabilityStatusIn(UUID stallId, List<AvailabilityStatus> statuses);

    // Count available stalls for an event — for dashboard metrics
    long countByEventIdAndAvailabilityStatusAndActiveForEventTrue(
        UUID eventId, AvailabilityStatus status);

    // Bulk status update — used when event is cancelled
    @Transactional
    @Modifying
    @Query("""
        UPDATE EventStall es
        SET es.availabilityStatus = :status
        WHERE es.event.id = :eventId
        AND es.availabilityStatus = 'AVAILABLE'
        """)
    int bulkUpdateStatusForEvent(
        @Param("eventId") UUID eventId,
        @Param("status") AvailabilityStatus status);

    // Delete all event stalls for an event
    // Used when event is cancelled and EventSpaceBooking is revoked
    @Transactional
    @Modifying
    @Query("DELETE FROM EventStall es WHERE es.event.id = :eventId")
    int deleteByEventId(@Param("eventId") UUID eventId);
}
