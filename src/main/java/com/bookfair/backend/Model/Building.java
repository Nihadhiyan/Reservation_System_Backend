package com.bookfair.backend.model;

import java.math.BigDecimal;
import java.util.List;

import com.bookfair.backend.model.enums.BuildingType;


import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
    name = "buildings",
    indexes = {
        @Index(name = "idx_building_venue", columnList = "venue_id")
    },
    uniqueConstraints = 
        @UniqueConstraint(
            name = "uk_building_venue_name",
            columnNames = {"venue_id", "name"}
        )
)
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Building extends BaseEntity {

    @Column(nullable = false)
    @NotBlank(message = "Building name is required")
    private String name;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "xCoord", column = @Column(name = "building_x_coord")),
        @AttributeOverride(name = "yCoord", column = @Column(name = "building_y_coord")),
        @AttributeOverride(name = "width", column = @Column(name = "building_width")),
        @AttributeOverride(name = "height", column = @Column(name = "building_height"))
    })
    @Valid
    private LayoutPosition layoutPosition;

    @OneToMany(mappedBy = "building", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LayoutMarker> markers;

    @Column(name = "square_footage", nullable = false)
    @Min(value = 0, message = "Square footage must be non-negative")
    private Double squareFootage;

    @Column(name = "daily_rate", precision = 10, scale = 2)
    @DecimalMin(value = "0.0", inclusive = true, message = "Daily rate must be non-negative")
    private BigDecimal dailyRate;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BuildingType type;

    @OneToMany(mappedBy = "building", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OrderBy("levelNumber ASC")
    private List<Floor> floors;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Venue venue;

    @PrePersist
    @PreUpdate
    private void validateOutdoorFloorLimit() {
        if (type == BuildingType.OUTDOOR && floors != null && floors.size() > 1) {
            throw new IllegalStateException("Architectural Error: An OUTDOOR space cannot have more than one floor/level.");
        }
    }
}
