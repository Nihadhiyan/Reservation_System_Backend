package com.bookfair.backend.model;

import java.math.BigDecimal;

import com.bookfair.backend.model.enums.ConditionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "pricing_rules")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class PricingRule extends BaseEntity {

    @Column(nullable = false)
    @NotBlank(message = "Rule name is required")
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ConditionType conditionType; // e.g., ORG_TYPE, DURATION, SEASONAL

    @Column(nullable = false)
    private String conditionValue; // e.g., NON_PROFIT, >7_DAYS, SUMMER

    @Column(nullable = false)
    @PositiveOrZero(message = "Multiplier must be non-negative")
    private BigDecimal multiplier;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "priority")
    private Integer priority;

}
