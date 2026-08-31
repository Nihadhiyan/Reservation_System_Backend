package com.bookfair.backend.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import com.bookfair.backend.config.AppProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import com.bookfair.backend.model.User;
import com.bookfair.backend.model.OrganizationMember;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import java.util.List;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties appProperties;

    private final OrganizationMemberRepository memberRepository;

    private SecretKey cachedKey;

    public String generateAccessToken(User user) {

        Map<String, Object> claims = new HashMap<>();

        claims.put("roles", "ROLE_" + (user.getSystemRole() != null ? user.getSystemRole().name() : "CUSTOMER"));

        List<OrganizationMember> members = memberRepository.findByUserIdWithOrganizations(user.getId());
        Map<String, String> orgRoles = new HashMap<>();
        for (OrganizationMember member : members) {
            orgRoles.put(member.getOrganization().getId().toString(), member.getRole().name());
        }
        claims.put("org_roles", orgRoles);

        String token = Jwts.builder()
                .claims()
                .add(claims)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + appProperties.getSecurity().getAccessTokenExpirationMs()))
                .and()
                .signWith(getKey())
                .compact();

        return token;
    }

    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + appProperties.getSecurity().getRefreshTokenExpirationMs()))
                .signWith(getKey())
                .compact();
    }

    public String generatePasswordResetToken(User user) {
        return Jwts.builder()
                .claim("purpose", "RESET_PASSWORD")
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(
                        new Date(System.currentTimeMillis() + appProperties.getSecurity().getPasswordResetAndVerificationTokenExpirationMs()))
                .signWith(getKey())
                .compact();
    }

    public String generateVerificationToken(User user) {
        return Jwts.builder()
                .claim("purpose", "VERIFY_EMAIL")
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(
                        new Date(System.currentTimeMillis() + appProperties.getSecurity().getPasswordResetAndVerificationTokenExpirationMs()))
                .signWith(getKey())
                .compact();
    }

    public String generateInviteToken(String email) {
        return Jwts.builder()
                .claim("purpose", "ORG_INVITE")
                .subject(email)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                // Using refresh token expiration (7 days) for invites
                .expiration(
                        new Date(System.currentTimeMillis() + appProperties.getSecurity().getRefreshTokenExpirationMs()))
                .signWith(getKey())
                .compact();
    }

    @PostConstruct
    private void initKey() {
        byte[] keyBytes = Decoders.BASE64.decode(appProperties.getJwtSecret());
        this.cachedKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey getKey() {
        return this.cachedKey;
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaim(token, claims -> claims.getSubject()));
    }

    public String extractSubject(String token) {
        return extractClaim(token, claims -> claims.getSubject());
    }

    public Instant extractIssuedAt(String token) {
        return extractClaim(token, claims -> claims.getIssuedAt().toInstant());
    }

    public String extractJti(String token) {
        return extractClaim(token, claims -> claims.getId());
    }

    public String extractSystemRole(String token) {
        return extractClaim(token, claims -> claims.get("roles", String.class));
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> extractOrgRoles(String token) {
        return extractClaim(token, claims -> claims.get("org_roles", Map.class));
    }

    public String extractPurpose(String token) {
        return extractClaim(token, claims -> claims.get("purpose", String.class));
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimResolver) {

        final Claims claims = extractAllClaims(token);
        return claimResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getRemainingExpirationTime(String token) {

        Instant expiration = extractExpiration(token);
        long remaining = expiration.toEpochMilli() - System.currentTimeMillis();

        return remaining > 0 ? remaining : 0;
    }

    public boolean isTokenExpired(String token) {

        return extractExpiration(token).isBefore(Instant.now());
    }

    public Instant extractExpiration(String token) {
        return extractClaim(token, claims -> claims.getExpiration().toInstant());
    }

    public long getAccessTokenExpirationTime() {
        return appProperties.getSecurity().getAccessTokenExpirationMs();
    }

    public long getRefreshTokenExpirationTime() {
        return appProperties.getSecurity().getRefreshTokenExpirationMs();
    }

    public List<GrantedAuthority> extractAuthorities(String token) {

        List<GrantedAuthority> authorities = new ArrayList<>();

        String systemRole = extractSystemRole(token);

        if (systemRole != null && !systemRole.isBlank()) {
            authorities.add(new SimpleGrantedAuthority(systemRole));
        }

        Map<String, String> orgRoles = extractOrgRoles(token);

        if (orgRoles != null) {
            // Track which org-role values we've already granted a blanket ROLE_ authority
            // for, so a user with the same role (e.g. ORG_ADMIN) across multiple
            // organizations doesn't get duplicate authorities.
            Set<String> grantedRoleAuthorities = new HashSet<>();
            for (Map.Entry<String, String> entry : orgRoles.entrySet()) {
                // Per-org-scoped authority string, kept for any future fine-grained checks;
                // this alone can never satisfy hasRole/hasAnyRole since it isn't ROLE_-prefixed
                // and bakes the organization id into the middle of the string.
                String orgScopedAuthority = "ORG_" + entry.getKey() + "_" + entry.getValue();
                authorities.add(new SimpleGrantedAuthority(orgScopedAuthority));

                // Blanket "does this user hold this org role in ANY organization" authority,
                // in the ROLE_X form Spring's hasRole/hasAnyRole actually checks for.
                // Endpoints needing org-specific scoping (e.g. "is ORG_ADMIN of THIS
                // organization") must still verify that explicitly in the service layer,
                // same as they do today - this only fixes reachability of the annotation.
                String roleAuthority = "ROLE_" + entry.getValue();
                if (grantedRoleAuthorities.add(roleAuthority)) {
                    authorities.add(new SimpleGrantedAuthority(roleAuthority));
                }
            }
        }

        return authorities;
    }
}
