package com.bookfair.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.bookfair.backend.config.filter.IpBlastShieldFilter;
import com.bookfair.backend.config.filter.MaintenanceModeFilter;
import com.bookfair.backend.config.filter.UserQuotaFilter;
import com.bookfair.backend.security.JwtAuthEntryPoint;
import com.bookfair.backend.security.keycloak.KeycloakJwtAuthenticationConverter;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@AllArgsConstructor
public class SecurityConfig {

    private final JwtAuthEntryPoint authEntryPoint;
    private final IpBlastShieldFilter ipBlastShieldFilter;
    private final UserQuotaFilter userQuotaFilter;
    private final MaintenanceModeFilter maintenanceModeFilter;
    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http
                .csrf(customizer -> customizer.disable())
                .cors(Customizer.withDefaults())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint))
                .authorizeHttpRequests(request -> request
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Public browsing surface: the frontend's home/events/venues pages
                        // (HomePage, EventsPage, EventDetailsPage, VenuesPage, VenueDetailsPage)
                        // are unauthenticated routes by design — a visitor should be able to
                        // browse exhibitions and venues before creating an account. Every
                        // other events/venues endpoint (create/update/delete, stall
                        // assignment, etc.) still falls through to .anyRequest().authenticated()
                        // or its own @PreAuthorize below.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*", "/api/v1/events/*/stalls").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/venues", "/api/v1/venues/*", "/api/v1/venues/*/buildings").permitAll()
                        // "/actuator/health" alone is an EXACT match, not a prefix — it
                        // never covered /actuator/health/liveness or /readiness (what the
                        // Docker healthcheck and Kubernetes-style probes actually hit),
                        // which fell through to the SUPER_ADMIN rule below and made every
                        // health probe fail with 401. /** covers the sub-paths too.
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer token verification (signature + expiry, via Keycloak's JWKS) and
                // extraction is Spring's built-in resource-server filter; authorization
                // still comes entirely from our own DB via the custom converter above.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)))
                .addFilterBefore(ipBlastShieldFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(userQuotaFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(maintenanceModeFilter, UserQuotaFilter.class)
                .build();
    }

    // This was previously defined but never invoked, so @Async threads never actually
    // inherited the caller's SecurityContext. Wiring it at startup so any current or
    // future @Async code that reads SecurityContextHolder behaves as originally intended.
    @PostConstruct
    public void enableAuthForwarding() {
        // This tells Spring: "When you spawn an Async thread, copy the User's ID into
        // it!"
        SecurityContextHolder.setStrategyName((SecurityContextHolder.MODE_INHERITABLETHREADLOCAL));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

}
