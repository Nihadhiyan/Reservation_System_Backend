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
                        .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*", "/api/v1/events/*/stalls").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/venues", "/api/v1/venues/*", "/api/v1/venues/*/buildings").permitAll()
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
