package com.bookfair.backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

import com.bookfair.backend.model.enums.StallType;
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
@Table(name = "stalls", indexes = {
        @Index(name = "idx_stall_hall", columnList = "hall_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_stall_name_hall", columnNames = { "hall_id", "name" })
})
@Setter
@Getter
@ToString
@NoArgsConstructor
public class Stall extends BaseEntity {

    @Column(nullable = false)
    @NotBlank(message = "Stall name is required")
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hall_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Hall hall;

    @Enumerated(EnumType.STRING)
    @Column(name = "stall_type")
    private StallType stallType;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "xCoord", column = @Column(name = "stall_x_coord")),
            @AttributeOverride(name = "yCoord", column = @Column(name = "stall_y_coord")),
            @AttributeOverride(name = "width", column = @Column(name = "stall_width")),
            @AttributeOverride(name = "height", column = @Column(name = "stall_height"))
    })
    @Valid
    private LayoutPosition layout;

    @Column(name = "square_footage", nullable = false)
    @Min(value = 0, message = "Square footage must be non-negative")
    private Double squareFootage;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "daily_rate", precision = 10, scale = 2)
    @DecimalMin(value = "0.0", inclusive = true, message = "Daily rate must be non-negative")
    private BigDecimal dailyRate;
}