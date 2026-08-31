package com.bookfair.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

import com.bookfair.backend.model.enums.CurrencyCode;
import com.bookfair.backend.model.enums.RentType;
import com.bookfair.backend.model.enums.SettlementStatus;

@Entity
@Table(name = "event_settlements")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class EventSettlement extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_owner_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization venueOwner;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private CurrencyCode currency = CurrencyCode.USD;

    // Snapshotted fields
    @Column(name = "snapshotted_daily_rent_rate", precision = 10, scale = 2, nullable = false)
    @PositiveOrZero(message = "Snapshotted daily rent rate must be non-negative")
    private BigDecimal snapshottedDailyRentRate;

    @Column(name = "snapshotted_revenue_share_percentage", precision = 5, scale = 2)
    private BigDecimal snapshottedRevenueSharePercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshotted_rent_type", nullable = false)
    private RentType snapshottedRentType;

    // Financial tracking
    @Column(name = "total_rent_owed", precision = 10, scale = 2, nullable = false)
    @PositiveOrZero(message = "Total rent owed must be non-negative")
    private BigDecimal totalRentOwed = BigDecimal.ZERO;

    @Column(name = "amount_paid_to_owner", precision = 10, scale = 2, nullable = false)
    @PositiveOrZero(message = "Amount paid to owner must be non-negative")
    private BigDecimal amountPaidToOwner = BigDecimal.ZERO;

    @Column(name = "remaining_balance", precision = 10, scale = 2, nullable = false)
    private BigDecimal remainingBalance = BigDecimal.ZERO;

    @Column(name = "organizer_profit", precision = 10, scale = 2, nullable = false)
    private BigDecimal organizerProfit = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementStatus status = SettlementStatus.LIABILITY_PENDING;
}
