package com.bookfair.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.enums.EventStatus;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByStatusAndActiveTrue(EventStatus status);

    Optional<Event> findByIdAndActiveTrue(UUID id);

    List<Event> findByOrganizerId(UUID orgId);

    List<Event> findByVenueIdAndActiveTrue(UUID venueId);

    boolean existsByNameAndActiveTrue(String name);

    List<Event> findByPartners_Id(UUID orgId);

    List<Event> findByStartDateTimeBeforeAndEndDateTimeAfter(
        Instant currentDate1,
        Instant currentDate2
    );

    List<Event> findByStartDateTimeBeforeAndEndDateTimeAfterAndActiveTrue(
        Instant currentDate1,
        Instant currentDate2
    );

    List<Event> findAllByOrganizerIdAndActiveTrue(UUID orgId);

    @Query("""
        SELECT e FROM Event e
        JOIN e.venue v
        WHERE v.id = :venueId
        AND e.status NOT IN ('CANCELLED', 'COMPLETED')
        AND e.active = true
        AND e.startDateTime < :requestedEnd
        AND e.endDateTime > :requestedStart
        """)
    List<Event> findConflictingEventsForVenue(
        @Param("venueId") UUID venueId,
        @Param("requestedStart") Instant requestedStart,
        @Param("requestedEnd") Instant requestedEnd);

    @Query("""
        SELECT e FROM Event e
        WHERE e.venue.id = (SELECT h.floor.building.venue.id FROM Hall h WHERE h.id = :hallId)
        AND e.status NOT IN ('CANCELLED', 'COMPLETED')
        AND e.active = true
        AND e.startDateTime < :requestedEnd
        AND e.endDateTime > :requestedStart
        """)
    List<Event> findConflictingEventsForHall(
        @Param("hallId") UUID hallId,
        @Param("requestedStart") Instant requestedStart,
        @Param("requestedEnd") Instant requestedEnd);

    @Query("""
        SELECT e FROM Event e
        WHERE e.venue.id = :venueId
        AND e.status NOT IN ('CANCELLED', 'COMPLETED')
        AND e.active = true
        AND e.endDateTime > :now
        """)
    List<Event> findUpcomingOrOngoingEventsForVenue(
        @Param("venueId") UUID venueId,
        @Param("now") Instant now);

    @Query("""
        SELECT v.id FROM Venue v
        WHERE v.active = true
        AND v.id NOT IN (
            SELECT e.venue.id FROM Event e
            WHERE e.status NOT IN ('CANCELLED', 'COMPLETED')
            AND e.active = true
            AND e.startDateTime < :requestedEnd
            AND e.endDateTime > :requestedStart
        )
        """)
    List<UUID> findAvailableVenueIds(
        @Param("requestedStart") Instant requestedStart,
        @Param("requestedEnd") Instant requestedEnd);
}