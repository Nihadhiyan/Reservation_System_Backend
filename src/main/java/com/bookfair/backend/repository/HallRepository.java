package com.bookfair.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import com.bookfair.backend.model.Hall;
import com.bookfair.backend.model.enums.HallType;

@Repository
public interface HallRepository extends JpaRepository<Hall, UUID> {

    List<Hall> findByFloorIdAndActiveTrue(UUID floorId);

    List<Hall> findByFloorBuildingVenueIdAndActiveTrue(UUID venueId);

    List<Hall> findByHallTypeAndActiveTrue(HallType hallType);

    @Query("SELECT h.floor.building.venue.id FROM Hall h WHERE h.id = :hallId")
    Optional<UUID> findVenueIdByHallId(@Param("hallId") UUID hallId);

    List<Hall> findByActiveTrue();

    long countById(UUID Id);

    boolean existsByIdAndActiveTrue(UUID id);

    /**
     * Locks the Hall row for the duration of the transaction so that Hall resize
     * and stall placement/generation cannot interleave and produce out-of-bounds stalls.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT h FROM Hall h WHERE h.id = :id")
    Optional<Hall> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}