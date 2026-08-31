package com.bookfair.backend.model;



import com.bookfair.backend.model.enums.FeatureType;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "layout_markers")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class LayoutMarker extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hall_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Hall hall;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureType type;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "xCoord", column = @Column(name = "marker_x")),
            @AttributeOverride(name = "yCoord", column = @Column(name = "marker_y")),
            @AttributeOverride(name = "width", column = @Column(name = "marker_width")),
            @AttributeOverride(name = "height", column = @Column(name = "marker_height"))
    })
    @Valid
    private LayoutPosition layout;

    @Column(nullable = false)
    @NotBlank(message = "Marker label is required")
    private String label;

    @Column(name = "primary_marker")
    private Boolean primaryMarker = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @PrePersist
    @PreUpdate
    private void validateSingleParent() {
        int parentCount = 0;
        if (venue != null) parentCount++;
        if (building != null) parentCount++;
        if (hall != null) parentCount++;

        if (parentCount == 0) {
            throw new IllegalStateException("LayoutMarker must be associated with at least one parent entity (Venue, Building, or Hall).");
        }
        if (parentCount > 1) {
            throw new IllegalStateException("LayoutMarker cannot be associated with more than one parent entity.");
        }
    }
}