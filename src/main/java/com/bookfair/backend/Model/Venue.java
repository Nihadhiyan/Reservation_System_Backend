package com.bookfair.backend.model;

import java.util.List;

import com.bookfair.backend.converter.PiiEncryptionConverter;
import com.bookfair.backend.model.enums.RentType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.math.BigDecimal;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "venues", indexes = {
        @Index(name = "idx_venue_name", columnList = "name")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Venue extends BaseEntity {

    @Column(nullable = false, unique = true)
    @NotBlank(message = "Venue name is required")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "address", nullable = false)
    @Convert(converter = PiiEncryptionConverter.class)
    private String address;

    @Column(name = "city", nullable = false)
    @Convert(converter = PiiEncryptionConverter.class)
    private String city;

    @Column(name = "country", nullable = false)
    @Convert(converter = PiiEncryptionConverter.class)
    private String country;

    @Column(name = "postal_code")
    @Convert(converter = PiiEncryptionConverter.class)
    private String postalCode;

    @Column(name = "contact_number")
    @Convert(converter = PiiEncryptionConverter.class)
    private String contactNumber;

    @Column(name = "email", nullable = false)
    @Email(message = "Email should be valid")
    @Convert(converter = PiiEncryptionConverter.class)
    private String email;

    @Column(name = "website")
    private String website;

    @Column(name = "latitude")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
    private Double latitude;

    @Column(name = "longitude")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
    private Double longitude;

    @Column(name = "google_place_id")
    private String googlePlaceId;

    @Column(name = "map_image_url")
    private String mapImageUrl;

    @Column(name = "total_square_footage", nullable = false)
    @Min(value = 0, message = "Total square footage must be non-negative")
    private Double totalSquareFootage;

    @Column(name = "parking_available")
    private Boolean parkingAvailable = false;

    @Column(name = "food_court_available")
    private Boolean foodCourtAvailable = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "daily_rent_rate", precision = 10, scale = 2)
    @DecimalMin(value = "0.0", inclusive = true, message = "Daily rent rate must be non-negative")
    private BigDecimal dailyRentRate;

    @Column(name = "revenue_share_percentage", precision = 5, scale = 2)
    @DecimalMin(value = "0.0", message = "Revenue share percentage must be >= 0")
    @DecimalMax(value = "100.0", message = "Revenue share percentage must be <= 100")
    private BigDecimal revenueSharePercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "rent_type")
    private RentType rentType;

    @Column(name = "venue_blueprint_image_url")
    private String blueprintImageUrl;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LayoutMarker> markers;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Building> buildings;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization owner;

    @ManyToMany
    @JoinTable(name = "venue_partners", joinColumns = @JoinColumn(name = "venue_id"), inverseJoinColumns = @JoinColumn(name = "organization_id"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Organization> partners;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Event> events;

}
