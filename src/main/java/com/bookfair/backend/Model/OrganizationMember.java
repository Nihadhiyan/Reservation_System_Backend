package com.bookfair.backend.model;

import jakarta.persistence.*;
import com.bookfair.backend.model.enums.OrganizationRole;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "organization_members",
    indexes = {
        @Index(name = "idx_org_member_user", columnList = "user_id"),
        @Index(name = "idx_org_member_org", columnList = "organization_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_org_member_user_org",
            columnNames = {"user_id", "organization_id"}
        )
    }
)
@Getter
@Setter
@ToString
@NoArgsConstructor
public class OrganizationMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationRole role;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
