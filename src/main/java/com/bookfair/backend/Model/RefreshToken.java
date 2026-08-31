package com.bookfair.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;


@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_token_jti", columnList = "jti", unique = true),
        @Index(name = "idx_refresh_token_user", columnList = "user_id"),
        @Index(name = "idx_rt_family", columnList = "family_id")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

    /**
     * The cryptographically secure refresh token string (or JWT).
     * Indexed and unique to ensure fast O(1) lookups during the /refresh-token
     * flow.
     */
    @Column(nullable = false, unique = true)
    @NotBlank(message = "Token string is required")
    private String jti;

    @Column(name = "family_id", nullable = false, updatable = false)
    private String familyId;

    /**
     * The authenticated user owning this device session.
     * Lazy fetched to avoid unnecessary join overhead during routine token lookups.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    /**
     * The client IP address from which the session was initiated.
     * Supports IPv6 addresses up to 45 characters.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * The client device information extracted from the User-Agent header.
     * Helps users identify recognized sessions (e.g., "Chrome on macOS").
     */
    @Column(name = "device_info", length = 512)
    private String deviceInfo;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    public boolean isExpired() {
        Objects.requireNonNull(this.expiryDate, "Expiry date cannot be null during expiration check");
        return Instant.now().isAfter(this.expiryDate);
    }
}
