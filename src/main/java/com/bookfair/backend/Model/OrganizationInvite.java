package com.bookfair.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

import com.bookfair.backend.model.enums.OrganizationRole;

@Entity
@Table(name = "organization_invites", indexes = {
        @Index(name = "idx_org_invite_token", columnList = "token"),
        @Index(name = "idx_org_invite_email", columnList = "email"),
        @Index(name = "idx_org_invite_org", columnList = "organization_id")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
public class OrganizationInvite extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    @Column(nullable = false)
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", nullable = false)
    private OrganizationRole assignedRole;

    @Column(unique = true, nullable = false)
    private String token;

    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant expiresAt;

    @Column(nullable = false)
    private Boolean used = false;
}
