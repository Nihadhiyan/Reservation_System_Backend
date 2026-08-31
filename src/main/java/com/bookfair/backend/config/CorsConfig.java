package com.bookfair.backend.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.web.filter.CorsFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final AppProperties appProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);
        // Only loopback origins are trusted by default; every other allowed origin must be
        // explicitly configured via app.cors.allowed-origins. A blanket "https://*" pattern
        // combined with allowCredentials(true) would accept credentialed requests from any
        // HTTPS origin on the internet — too broad a default trust boundary.
        List<String> patterns = new ArrayList<>(List.of("http://localhost:*", "http://127.0.0.1:*"));
        if (appProperties.getCors().getAllowedOrigins() != null) {
            patterns.addAll(appProperties.getCors().getAllowedOrigins());
        }
        config.setAllowedOriginPatterns(patterns);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public CorsFilter corsFilter() {
        return new CorsFilter(Objects.requireNonNull(corsConfigurationSource()));
    }
}