package com.bookfair.backend.model;

import java.time.Instant;
import java.util.List;

import com.bookfair.backend.model.enums.EventStatus;
import com.bookfair.backend.model.enums.EventType;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
    name = "events",
    indexes = {
        @Index(name = "idx_reservation_book_fair", columnList = "name"), 
        @Index(name = "idx_venue_book_fair", columnList = "venue_id")
    }
)
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Event extends BaseEntity {
    
    @Column(nullable = false, unique = true)
    @NotBlank(message = "Book fair name is required")
    private String name;

    @Column(name = "start_date", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant startDateTime;

    @Column(name = "end_date", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant endDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organizer;

    @ManyToMany
    @JoinTable(
        name = "event_partners",
        joinColumns = @JoinColumn(name = "event_id"),
        inverseJoinColumns = @JoinColumn(name = "organization_id")
    )
    private List<Organization> partners;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @AssertTrue(message = "End date must be after start date")
    public boolean isValidDateRange() {
        return startDateTime == null || endDateTime == null || endDateTime.isAfter(startDateTime);
    }

}
