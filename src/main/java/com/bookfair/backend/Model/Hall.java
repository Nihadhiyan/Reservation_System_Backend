package com.bookfair.backend.model;

import java.math.BigDecimal;
import java.util.List;

import com.bookfair.backend.model.enums.BuildingType;
import com.bookfair.backend.model.enums.HallType;
import com.bookfair.backend.model.enums.SpaceCategory;


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
@Table(name = "halls", indexes = {
        @Index(name = "idx_hall_floor", columnList = "floor_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_hall_floor_name", columnNames = { "floor_id", "name" })
})
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Hall extends BaseEntity {

    @Column(nullable = false)
    @NotBlank(message = "Hall name is required")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "space_category", nullable = false)
    private SpaceCategory spaceCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "hall_type", nullable = false)
    private HallType hallType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Floor floor;

    @OneToMany(mappedBy = "hall", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Stall> stalls;

    @Column(name = "blueprint_image_url", length = 2048)
    private String blueprintImageUrl;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "xCoord", column = @Column(name = "hall_x_coord")),
            @AttributeOverride(name = "yCoord", column = @Column(name = "hall_y_coord")),
            @AttributeOverride(name = "width", column = @Column(name = "hall_width")),
            @AttributeOverride(name = "height", column = @Column(name = "hall_height"))
    })
    @Valid
    private LayoutPosition layout;

    @Column(name = "square_footage")
    @Min(value = 0, message = "Square footage must be non-negative")
    private Double squareFootage;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "daily_rate", precision = 10, scale = 2)
    @DecimalMin(value = "0.0", inclusive = true, message = "Daily rate must be non-negative")
    private BigDecimal dailyRate;

    @Column(name = "max_stalls")
    @Min(value = 0, message = "Max stalls must be non-negative")
    private Integer maxStalls;

    @Column(name = "wifi_available")
    private Boolean wifiAvailable = false;

    @Column(name = "air_conditioned")
    private Boolean airConditioned = false;

    @OneToMany(mappedBy = "hall", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LayoutMarker> markers;


    @PrePersist
    @PreUpdate
    private void validateHallPlacement() {
        if (this.floor != null && this.floor.getBuilding() != null) {
            BuildingType parentType = this.floor.getBuilding().getType();

            // Cannot put an OUTDOOR hall in a strictly INDOOR building
            if (parentType == BuildingType.INDOOR && this.spaceCategory == SpaceCategory.OUTDOOR) {
                throw new IllegalStateException("Architectural Error: Cannot place an OUTDOOR hall inside a strictly INDOOR building.");
            }
            
            // Cannot put an INDOOR hall in a strictly OUTDOOR building
            if (parentType == BuildingType.OUTDOOR && this.spaceCategory == SpaceCategory.INDOOR) {
                throw new IllegalStateException("Architectural Error: Cannot place an INDOOR hall inside a strictly OUTDOOR space.");
            }
            
            // If the building is HYBRID, both INDOOR and OUTDOOR halls are completely valid!
        }
    }
}
