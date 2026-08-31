package com.bookfair.backend.model;

import java.math.BigDecimal;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "floors", indexes = {
        @Index(name = "idx_floor_building", columnList = "building_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_floor_building_level", columnNames = { "building_id", "level_number" })
})
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Floor extends BaseEntity {


    @Column(name = "level_name", nullable = false)
    @NotBlank(message = "Level name is required")
    private String levelName;

    @Column(name = "level_number", nullable = false)
    @NotNull(message = "Level number is required")
    @Min(value = 0, message = "Level number must be a non-negative integer")
    private Integer levelNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Building building;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "daily_rate", precision = 10, scale = 2)
    @DecimalMin(value = "0.0", inclusive = true, message = "Daily rate must be non-negative")
    private BigDecimal dailyRate;

    @OneToMany(mappedBy = "floor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OrderBy("name ASC")
    private List<Hall> halls;
}
