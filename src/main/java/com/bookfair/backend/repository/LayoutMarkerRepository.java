package com.bookfair.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bookfair.backend.model.LayoutMarker;
import com.bookfair.backend.model.enums.FeatureType;

@Repository
public interface LayoutMarkerRepository extends JpaRepository<LayoutMarker, UUID> {

    List<LayoutMarker> findByVenueIdAndActiveTrue(UUID venueId);

    List<LayoutMarker> findByBuildingIdAndActiveTrue(UUID buildingId);

    List<LayoutMarker> findByHallIdAndActiveTrue(UUID hallId);

    List<LayoutMarker> findByTypeAndActiveTrue(FeatureType type);
}