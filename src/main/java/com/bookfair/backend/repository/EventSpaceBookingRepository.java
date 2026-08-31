package com.bookfair.backend.repository;

import com.bookfair.backend.model.EventSpaceBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface EventSpaceBookingRepository extends JpaRepository<EventSpaceBooking, UUID> {

    Page<EventSpaceBooking> findByEventId(UUID eventId, Pageable pageable);

    long countByEventId(UUID eventId);

    @Query("SELECT COUNT(b) FROM EventSpaceBooking b WHERE b.event.id = :eventId AND b.status IN :statuses")
    long countByEventIdAndStatusIn(@Param("eventId") UUID eventId,
            @Param("statuses") List<com.bookfair.backend.model.enums.BookingStatus> statuses);

    @Query("SELECT b FROM EventSpaceBooking b WHERE b.event.id = :eventId AND b.status IN :statuses")
    List<EventSpaceBooking> findByEventIdAndStatusIn(@Param("eventId") UUID eventId,
            @Param("statuses") List<com.bookfair.backend.model.enums.BookingStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
        SELECT b FROM EventSpaceBooking b
        WHERE b.event.id != :eventId
        AND b.status NOT IN ('CANCELLED', 'EXPIRED')
        AND b.startsAt < :endsAt
        AND b.endsAt > :startsAt
        AND (
            (b.bookingLevel = 'VENUE' AND b.venue.id = :venueId)
            OR (b.bookingLevel = 'BUILDING' AND b.building.venue.id = :venueId)
            OR (b.bookingLevel = 'FLOOR' AND b.floor.building.venue.id = :venueId)
            OR (b.bookingLevel = 'HALL' AND b.hall.floor.building.venue.id = :venueId)
            OR (b.bookingLevel = 'STALL' AND b.stall.hall.floor.building.venue.id = :venueId)
        )
        """)
    List<EventSpaceBooking> findConflictsForVenue(
        @Param("eventId") UUID eventId,
        @Param("venueId") UUID venueId,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
        SELECT b FROM EventSpaceBooking b
        WHERE b.event.id != :eventId
        AND b.status NOT IN ('CANCELLED', 'EXPIRED')
        AND b.startsAt < :endsAt
        AND b.endsAt > :startsAt
        AND (
            (b.bookingLevel = 'VENUE' AND b.venue.id = :venueId)
            OR (b.bookingLevel = 'BUILDING' AND b.building.id = :buildingId)
            OR (b.bookingLevel = 'FLOOR' AND b.floor.building.id = :buildingId)
            OR (b.bookingLevel = 'HALL' AND b.hall.floor.building.id = :buildingId)
            OR (b.bookingLevel = 'STALL' AND b.stall.hall.floor.building.id = :buildingId)
        )
        """)
    List<EventSpaceBooking> findConflictsForBuilding(
        @Param("eventId") UUID eventId,
        @Param("venueId") UUID venueId,
        @Param("buildingId") UUID buildingId,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
        SELECT b FROM EventSpaceBooking b
        WHERE b.event.id != :eventId
        AND b.status NOT IN ('CANCELLED', 'EXPIRED')
        AND b.startsAt < :endsAt
        AND b.endsAt > :startsAt
        AND (
            (b.bookingLevel = 'VENUE' AND b.venue.id = :venueId)
            OR (b.bookingLevel = 'BUILDING' AND b.building.id = :buildingId)
            OR (b.bookingLevel = 'FLOOR' AND b.floor.id = :floorId)
            OR (b.bookingLevel = 'HALL' AND b.hall.floor.id = :floorId)
            OR (b.bookingLevel = 'STALL' AND b.stall.hall.floor.id = :floorId)
        )
        """)
    List<EventSpaceBooking> findConflictsForFloor(
        @Param("eventId") UUID eventId,
        @Param("venueId") UUID venueId,
        @Param("buildingId") UUID buildingId,
        @Param("floorId") UUID floorId,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
        SELECT b FROM EventSpaceBooking b
        WHERE b.event.id != :eventId
        AND b.status NOT IN ('CANCELLED', 'EXPIRED')
        AND b.startsAt < :endsAt
        AND b.endsAt > :startsAt
        AND (
            (b.bookingLevel = 'VENUE' AND b.venue.id = :venueId)
            OR (b.bookingLevel = 'BUILDING' AND b.building.id = :buildingId)
            OR (b.bookingLevel = 'FLOOR' AND b.floor.id = :floorId)
            OR (b.bookingLevel = 'HALL' AND b.hall.id = :hallId)
            OR (b.bookingLevel = 'STALL' AND b.stall.hall.id = :hallId)
        )
        """)
    List<EventSpaceBooking> findConflictsForHall(
        @Param("eventId") UUID eventId,
        @Param("venueId") UUID venueId,
        @Param("buildingId") UUID buildingId,
        @Param("floorId") UUID floorId,
        @Param("hallId") UUID hallId,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
        SELECT b FROM EventSpaceBooking b
        WHERE b.event.id != :eventId
        AND b.status NOT IN ('CANCELLED', 'EXPIRED')
        AND b.startsAt < :endsAt
        AND b.endsAt > :startsAt
        AND (
            (b.bookingLevel = 'VENUE' AND b.venue.id = :venueId)
            OR (b.bookingLevel = 'BUILDING' AND b.building.id = :buildingId)
            OR (b.bookingLevel = 'FLOOR' AND b.floor.id = :floorId)
            OR (b.bookingLevel = 'HALL' AND b.hall.id = :hallId)
            OR (b.bookingLevel = 'STALL' AND b.stall.id = :stallId)
        )
        """)
    List<EventSpaceBooking> findConflictsForStall(
        @Param("eventId") UUID eventId,
        @Param("venueId") UUID venueId,
        @Param("buildingId") UUID buildingId,
        @Param("floorId") UUID floorId,
        @Param("hallId") UUID hallId,
        @Param("stallId") UUID stallId,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt);

    @Modifying
    @Query("UPDATE EventSpaceBooking b SET b.status = 'EXPIRED' WHERE b.status = 'PENDING' AND b.createdAt < :cutoff")
    int expirePendingBookingsOlderThan(@Param("cutoff") Instant cutoff);

    @Query("SELECT COUNT(b) FROM EventSpaceBooking b WHERE b.venue.id = :venueId AND b.status NOT IN ('CANCELLED', 'EXPIRED') AND b.endsAt > :now")
    long countActiveByVenueId(@Param("venueId") UUID venueId, @Param("now") Instant now);

    @Query("SELECT COUNT(b) FROM EventSpaceBooking b WHERE b.building.id = :buildingId AND b.status NOT IN ('CANCELLED', 'EXPIRED') AND b.endsAt > :now")
    long countActiveByBuildingId(@Param("buildingId") UUID buildingId, @Param("now") Instant now);

    @Query("SELECT COUNT(b) FROM EventSpaceBooking b WHERE b.floor.id = :floorId AND b.status NOT IN ('CANCELLED', 'EXPIRED') AND b.endsAt > :now")
    long countActiveByFloorId(@Param("floorId") UUID floorId, @Param("now") Instant now);

    @Query("SELECT COUNT(b) FROM EventSpaceBooking b WHERE b.hall.id = :hallId AND b.status NOT IN ('CANCELLED', 'EXPIRED') AND b.endsAt > :now")
    long countActiveByHallId(@Param("hallId") UUID hallId, @Param("now") Instant now);

    @Query("SELECT COUNT(b) FROM EventSpaceBooking b WHERE b.stall.id = :stallId AND b.status NOT IN ('CANCELLED', 'EXPIRED') AND b.endsAt > :now")
    long countActiveByStallId(@Param("stallId") UUID stallId, @Param("now") Instant now);
}
