package com.bookfair.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bookfair.backend.model.Floor;

@Repository
public interface FloorRepository extends JpaRepository<Floor, UUID> {
    List<Floor> findByBuildingId(UUID buildingId);
    List<Floor> findByBuildingIdOrderByLevelNumberAsc(UUID buildingId);
    List<Floor> findByBuildingIdAndActiveTrue(UUID buildingId);
    
    @Query("SELECT f.building.venue.id FROM Floor f WHERE f.id = :floorId")
    Optional<UUID> findVenueIdByFloorId(@Param("floorId") UUID floorId);

    Optional<Floor> findByIdAndActiveTrue(UUID id);
    boolean existsByIdAndActiveTrue(UUID id);
}
