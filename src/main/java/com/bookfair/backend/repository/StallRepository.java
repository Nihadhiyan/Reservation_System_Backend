package com.bookfair.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import com.bookfair.backend.model.Stall;
import com.bookfair.backend.model.enums.StallType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StallRepository extends JpaRepository<Stall, UUID> {

    List<Stall> findByHallIdAndActiveTrue(UUID hallId);

    @Query("""
        SELECT s FROM Stall s 
        JOIN s.hall h JOIN h.floor f JOIN f.building b 
        WHERE b.venue.id = :venueId AND s.active = true
    """)
    List<Stall> findByVenueIdAndActiveTrue(@Param("venueId") UUID venueId);

    @Query("""
        SELECT s FROM Stall s 
        JOIN s.hall h JOIN h.floor f 
        WHERE f.building.id = :buildingId AND s.active = true
    """)
    List<Stall> findByBuildingIdAndActiveTrue(@Param("buildingId") UUID buildingId);

    @Query("""
        SELECT s FROM Stall s 
        JOIN s.hall h 
        WHERE h.floor.id = :floorId AND s.active = true
    """)
    List<Stall> findByFloorIdAndActiveTrue(@Param("floorId") UUID floorId);

    @Query("SELECT s.hall.floor.building.venue.id FROM Stall s WHERE s.id = :stallId")
    Optional<UUID> findVenueIdByStallId(@Param("stallId") UUID stallId);
    
    long countByHallIdAndActiveTrue(UUID hallId);
    
    @Query("""
        SELECT s FROM Stall s 
        JOIN s.hall h JOIN h.floor f JOIN f.building b 
        WHERE b.venue.id = (SELECT e.venue.id FROM Event e WHERE e.id = :eventId)
        AND s.active = true
        AND NOT EXISTS (
            SELECT 1 FROM EventStall es WHERE es.stall = s AND es.event.id = :eventId AND es.activeForEvent = true
        )
        """)
    Page<Stall> findAvailableStallsByEventId(@org.springframework.data.repository.query.Param("eventId") UUID eventId, org.springframework.data.domain.Pageable pageable);

    List<Stall> findByHallIdAndActiveFalse(UUID hallId);

    /**
     * Locks the Stall row for the duration of the transaction to serialize concurrent
     * event-assignment attempts for the same stall (prevents duplicate EventStall races).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stall s where s.id = :id")
    Optional<Stall> findByIdForUpdate(UUID id);
        
    List<Stall> findByStallTypeAndActiveTrue(StallType stallType);

    List<Stall> findAllByActiveTrue();

    List<Stall> findAllByIdInAndActiveTrue(List<UUID> stallIds);
        
    Optional<Stall> findByIdAndActiveTrue(UUID id);

    long countByActiveTrue();

    long countByHallId(UUID hallId);

    boolean existsByHallIdAndName(UUID hallId, String name);
}
