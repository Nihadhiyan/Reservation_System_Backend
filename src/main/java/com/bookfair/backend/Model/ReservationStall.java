package com.bookfair.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "reservation_stalls",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_reservation_stall",
            columnNames = {"reservation_id", "event_stall_id"})
    },
    indexes = {
        @Index(name = "idx_res_stall_reservation_id", columnList = "reservation_id"),
        @Index(name = "idx_res_stall_event_stall_id", columnList = "event_stall_id")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationStall extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // Which reservation this belongs to
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Reservation reservation;

    // Which event stall slot the vendor is reserving
    // EventStall carries the organizer's customized view
    // of the stall within their specific event
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_stall_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EventStall eventStall;
}
