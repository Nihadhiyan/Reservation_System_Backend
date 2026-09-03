package com.bookfair.backend.security;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import com.bookfair.backend.config.AppProperties;
import org.springframework.stereotype.Service;

import com.bookfair.backend.model.User;

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

    private SecretKey cachedKey;

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

    public boolean isTokenExpired(String token) {

        return extractExpiration(token).isBefore(Instant.now());
    }

    public Instant extractExpiration(String token) {
        return extractClaim(token, claims -> claims.getExpiration().toInstant());
    }
}
