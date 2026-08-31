package com.bookfair.backend.config.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.UnauthorizedException;
import com.bookfair.backend.security.JwtService;
import com.bookfair.backend.service.TokenBlacklistService;
import com.bookfair.backend.service.AdminService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final AdminService adminService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            TokenBlacklistService tokenBlacklistService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
            AdminService adminService
        ) {
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.adminService = adminService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Invalid Authorization header");

            if (enforceMaintenanceMode(request, response)) return;

            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        String jti = null;
        UUID userId = null;
        String roles = null;
        Instant issuedAt = null;

        try {
            Claims claims = jwtService.extractAllClaims(token);
            jti = claims.getId();
            userId = UUID.fromString(claims.getSubject());
            roles = claims.get("roles", String.class);
            issuedAt = claims.getIssuedAt().toInstant();
        } catch (Exception e) {
            log.error("JWT Token cryptographic verification failed: {}", e.getMessage());
            handlerExceptionResolver.resolveException(request, response, null,
                    new UnauthorizedException("Invalid or malformed authentication token", ErrorCode.UNAUTHORIZED));
            return;
        }

        try {
            if (tokenBlacklistService.isAccessTokenBlacklisted(jti)) {
                log.warn("Security Alert: Intercepted request attempting to use a blacklisted JWT token [JTI: {}].",
                        jti);
                handlerExceptionResolver.resolveException(request, response, null,
                        new UnauthorizedException("Token has been revoked", ErrorCode.UNAUTHORIZED));
                return;
            }
        } catch (Exception e) {
            log.error("Token Blacklist Service blacklist lookup failed: {}", e.getMessage(), e);
        }

        if (userId != null && issuedAt != null) {
            Long checkpointEpochSeconds = tokenBlacklistService.getSecurityCheckpoint(userId);

            if (checkpointEpochSeconds != null) {
                long tokenIssuedEpochSeconds = issuedAt.getEpochSecond();

                if (tokenIssuedEpochSeconds < checkpointEpochSeconds) {
                    log.warn(
                            "Time-Travel Firewall Triggered: Token issued at {} is predated by security checkpoint {} for user [{}]",
                            tokenIssuedEpochSeconds, checkpointEpochSeconds, userId);
                    handlerExceptionResolver.resolveException(request, response, null,
                            new UnauthorizedException("Session invalidated by a recent security event",
                                    ErrorCode.UNAUTHORIZED));
                    return;
                }
            }
        }

        if (userId != null && roles != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                if (!jwtService.isTokenExpired(token)) {
                    List<GrantedAuthority> authorities = jwtService.extractAuthorities(token);

                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            userId, null, authorities);
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else {
                    handlerExceptionResolver.resolveException(request, response, null,
                            new UnauthorizedException("Token has expired", ErrorCode.UNAUTHORIZED));
                    return;
                }

            } catch (Exception e) {
                log.warn("Stateless authentication reconstruction failed for user [{}]: {}", userId, e.getMessage());
                handlerExceptionResolver.resolveException(request, response, null,
                        new UnauthorizedException("Invalid authentication state", ErrorCode.UNAUTHORIZED));
                return;
            }
        }

        if (enforceMaintenanceMode(request, response)) return;

        filterChain.doFilter(request, response);
    }

    private boolean enforceMaintenanceMode(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (adminService.isMaintenanceMode()) {
            boolean isSuperAdmin = false;
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                isSuperAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
            }

            // Allow public auth routes to ensure a super admin can actually log in during maintenance
            if (!isSuperAdmin && request.getRequestURI() != null && !request.getRequestURI().contains("/auth/")) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"System is under maintenance\"}");
                return true; // Request is handled, stop filter chain
            }
        }
        return false;
    }
}
