package com.bookfair.backend.model;

import com.bookfair.backend.model.enums.AvailabilityStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "event_stalls",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_event_stall_event_stall",
            columnNames = {"event_id", "stall_id"})
    },
    indexes = {
        @Index(name = "idx_event_stall_event_id", columnList = "event_id"),
        @Index(name = "idx_event_stall_stall_id", columnList = "stall_id"),
        @Index(name = "idx_event_stall_status", columnList = "availability_status")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStall extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // Which event this stall configuration belongs to
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Event event;

    // The physical stall from the venue owner's permanent layout
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stall_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Stall stall;

    // Whether the organizer has included this stall in their event
    // false = organizer disabled this stall (e.g. used space for a stage)
    // This does NOT affect the venue owner's permanent layout
    @Column(name = "active_for_event", nullable = false)
    @Builder.Default
    private Boolean activeForEvent = true;

    // Vendor booking status for this stall within this event
    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false)
    @Builder.Default
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.AVAILABLE;

    // Custom position set by the organizer for this event only
    // null = use the stall's original position from Stall.layout
    // The venue owner's original layout is never modified
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "xCoord",
            column = @Column(name = "custom_x")),
        @AttributeOverride(name = "yCoord",
            column = @Column(name = "custom_y")),
        @AttributeOverride(name = "width",
            column = @Column(name = "custom_width")),
        @AttributeOverride(name = "height",
            column = @Column(name = "custom_height"))
    })
    private LayoutPosition customLayout;

    // Custom name for this stall in this event only
    // e.g. venue calls it "S-101" but organizer labels it "Booth 1"
    // null = use original stall name
    @Column(name = "custom_name")
    private String customName;

    // Price override for this stall in this event
    // null = fall through to PricingService fallback chain
    @Column(name = "event_price", precision = 10, scale = 2)
    private BigDecimal eventPrice;

    // Returns the effective layout — custom if organizer set one,
    // original stall layout otherwise
    @Transient
    public LayoutPosition getEffectiveLayout() {
        return customLayout != null ? customLayout : stall.getLayout();
    }

    // Returns the effective display name
    @Transient
    public String getEffectiveName() {
        return customName != null ? customName : stall.getName();
    }

    // Returns the effective price — event override or null (caller uses PricingService)
    @Transient
    public BigDecimal getEffectivePrice() {
        return eventPrice;
    }
}
