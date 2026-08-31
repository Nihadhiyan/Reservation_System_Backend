package com.bookfair.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "genres")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Genre extends BaseEntity {

    @Column(unique = true, nullable = false)
    @NotBlank(message = "Genre name is required")
    private String name;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    private String color;
}
