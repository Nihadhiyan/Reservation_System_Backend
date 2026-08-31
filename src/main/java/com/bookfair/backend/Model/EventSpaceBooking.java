package com.bookfair.backend.model;

import jakarta.persistence.*;
import lombok.*;
import com.bookfair.backend.model.enums.BookingLevel;
import com.bookfair.backend.model.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "event_space_bookings")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class EventSpaceBooking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_level", nullable = false)
    private BookingLevel bookingLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Floor floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hall_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Hall hall;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stall_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Stall stall;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Reservation reservation;
}
