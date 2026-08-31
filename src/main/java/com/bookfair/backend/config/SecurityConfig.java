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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.bookfair.backend.config.filter.IpBlastShieldFilter;
import com.bookfair.backend.config.filter.JwtAuthenticationFilter;
import com.bookfair.backend.config.filter.UserQuotaFilter;
import com.bookfair.backend.security.JwtAuthEntryPoint;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@AllArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final JwtAuthEntryPoint authEntryPoint;
    private final IpBlastShieldFilter ipBlastShieldFilter;
    private final UserQuotaFilter userQuotaFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http
                .csrf(customizer -> customizer.disable())
                .cors(Customizer.withDefaults())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint))
                .authorizeHttpRequests(request -> request
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(ipBlastShieldFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtFilter, IpBlastShieldFilter.class)
                .addFilterAfter(userQuotaFilter, JwtAuthenticationFilter.class)
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
