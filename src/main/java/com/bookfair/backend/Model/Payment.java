package com.bookfair.backend.model;

import java.math.BigDecimal;

import com.bookfair.backend.model.enums.CurrencyCode;
import com.bookfair.backend.model.enums.PaymentGateway;
import com.bookfair.backend.model.enums.PaymentStatus;



import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_reservation", columnList = "reservation_id")
    }
)
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Reservation reservation;

    @Column(name = "transaction_id", unique = true)
    private String transactionId; // The receipt ID from your payment gateway

    @Column(nullable = false, precision = 10, scale = 2)
    @PositiveOrZero(message = "Payment amount must be non-negative")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_gateway", nullable = false)
    private PaymentGateway paymentGateway;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    
}
