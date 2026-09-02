package com.bookfair.backend.model;

import java.util.List;
import java.util.Set;

import com.bookfair.backend.model.enums.OrganizationCapability;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import com.bookfair.backend.converter.PiiEncryptionConverter;

@Entity
@Table(
    name = "organizations",
    indexes = {
        @Index(name = "idx_organization_active", columnList = "active"),
        @Index(name = "idx_registration_number", columnList = "registration_number")
    }
)
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Organization extends BaseEntity {
    
    @Column(nullable = false, unique = true)
    @NotBlank(message = "Organization name is required")
    private String name;

    @Column(name = "contact_number")
    @Convert(converter = PiiEncryptionConverter.class)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String contactNumber;

    @Column(name = "contact_email")
    @Email(message = "Contact email must be valid")
    @Convert(converter = PiiEncryptionConverter.class)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String contactEmail;

    @Column(name = "billing_address")
    @Convert(converter = PiiEncryptionConverter.class)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String billingAddress;

    @Column(name = "registration_number", nullable = false, unique = true)
    @Convert(converter = PiiEncryptionConverter.class)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String registrationNumber;

    @ElementCollection(targetClass = OrganizationCapability.class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "organization_capabilities",
        joinColumns = @JoinColumn(name = "organization_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false)
    private Set<OrganizationCapability> capabilities;

    @OneToMany(mappedBy = "organization")
    private List<OrganizationMember> employees;

    @OneToMany(mappedBy = "owner")
    private List<Venue> ownedVenues;

    @ManyToMany(mappedBy = "partners")
    private List<Venue> partnerVenues;

    @Column(name = "verified", nullable = false)
    private Boolean verified = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Embedded
    private DeletionAudit deletionAudit;

    public boolean isVenueOwner() {
        return capabilities != null && capabilities.contains(OrganizationCapability.OWNS_VENUES);
    }

    public boolean isEventOrganizer() {
        return capabilities != null && capabilities.contains(OrganizationCapability.ORGANIZES_EVENTS);
    }

    public boolean isVendor() {
        return capabilities != null && capabilities.contains(OrganizationCapability.OPERATES_STALLS);
    }
}