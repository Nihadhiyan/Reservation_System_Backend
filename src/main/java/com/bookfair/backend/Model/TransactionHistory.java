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
import com.bookfair.backend.model.enums.TransactionRole;

@Entity
@Table(name = "transaction_histories", indexes = {
        @Index(name = "idx_tx_event", columnList = "event_id"),
        @Index(name = "idx_tx_reservation", columnList = "reservation_id"),
        @Index(name = "idx_tx_payment", columnList = "payment_id"),
        @Index(name = "idx_tx_roles", columnList = "source_role, destination_role")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
public class TransactionHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private CurrencyCode currency;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    @PositiveOrZero(message = "Transaction amount must be non-negative")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_role", nullable = false)
    private TransactionRole sourceRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_role", nullable = false)
    private TransactionRole destinationRole;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Payment payment;
}
