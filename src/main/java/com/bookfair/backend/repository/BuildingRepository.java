package com.bookfair.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bookfair.backend.model.Building;
import com.bookfair.backend.model.enums.BuildingType;

@Repository
public interface BuildingRepository extends JpaRepository<Building, UUID> {

    List<Building> findByVenueIdAndActiveTrue(UUID venueId);

    @Query("SELECT b.venue.id FROM Building b WHERE b.id = :buildingId")
    Optional<UUID> findVenueIdByBuildingId(@Param("buildingId") UUID buildingId);

    List<Building> findByTypeAndActiveTrue(BuildingType buildingType);

    Optional<Building> findByIdAndActiveTrue(UUID id);
}
