package com.bookfair.backend.model;

import jakarta.persistence.*;
import com.bookfair.backend.model.enums.ReservationStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_reservation_user", columnList = "user_id"),
        @Index(name = "idx_reservation_event", columnList = "event_id"),
        @Index(name = "idx_reservation_expires", columnList = "expires_at"),
        @Index(name = "idx_reservation_status", columnList = "status")
})
@Setter
@Getter
@ToString
@NoArgsConstructor
public class Reservation extends BaseEntity {

    // employee from that organization who is managing the booth
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Event event;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<EventSpaceBooking> spaceBookings;

    @Column(name = "reservation_start_time", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant reservationStartDateTime;

    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Genre genre;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    @PositiveOrZero(message = "Total price must be non-negative")
    private BigDecimal totalPrice;

    @Column(name = "qr_code_payload", columnDefinition = "TEXT")
    private String qrCodePayload; // Stores the JWT String for the scanner app

    @AssertTrue(message = "Expires at must be after start time")
    public boolean isValidReservationTimeRange() {
        return reservationStartDateTime == null || expiresAt == null || expiresAt.isAfter(reservationStartDateTime);
    }
}
