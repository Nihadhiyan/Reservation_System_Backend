package com.bookfair.backend.model;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// Implements Serializable for Redis caching compatibility
@Embeddable
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class LayoutPosition implements Serializable {

    @Column(name = "x_coord", nullable = false)
    @Min(value = 0, message = "X coordinate must be non-negative")
    private Integer xCoord;

    @Column(name = "y_coord", nullable = false)
    @Min(value = 0, message = "Y coordinate must be non-negative")
    private Integer yCoord;

    @Column(nullable = false)
    @Min(value = 1, message = "Width must be positive")
    private Integer width;

    @Column(nullable = false)
    @Min(value = 1, message = "Height must be positive")
    private Integer height;
    
}
