package com.bookfair.backend.config.filter;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.bookfair.backend.service.AdminService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Blocks non-super-admin traffic while {@code AdminService.isMaintenanceMode()}
 * is on, except for auth routes so a super admin can still log in. Split out
 * of the old JwtAuthenticationFilter (which used to run this check inline)
 * when auth verification moved to Spring's OAuth2 resource server — this
 * concern is independent of how a request got authenticated, so it now runs
 * as its own filter, after authentication has been resolved.
 */
@Component
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private final AdminService adminService;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public MaintenanceModeFilter(
            AdminService adminService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.adminService = adminService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (adminService.isMaintenanceMode()) {
            boolean isSuperAdmin = false;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                isSuperAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
            }

            if (!isSuperAdmin && request.getRequestURI() != null && !request.getRequestURI().contains("/auth/")) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"System is under maintenance\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
