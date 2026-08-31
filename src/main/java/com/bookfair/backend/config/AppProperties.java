package com.bookfair.backend.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotBlank(message = "JWT Secret is missing!")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String jwtSecret;

    @Valid
    private final Cors cors = new Cors();
    @Valid
    private final Security security = new Security();

    @Data
    public static class Cors {
        @NotEmpty(message = "CORS allowed origins must be configured!")
        private List<String> allowedOrigins;
    }

    @Data
    public static class Security {
        @NotBlank(message = "PII encryption secret is missing!")
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private String piiSecret;

        @Positive(message = "Access token expiration time must be positive!")
        private long accessTokenExpirationMs = 3600000;

        @Positive(message = "Refresh token expiration time must be positive!")
        private long refreshTokenExpirationMs = 604800000;

        @Positive(message = "Password reset and verification token expiration time must be positive!")
        private long passwordResetAndVerificationTokenExpirationMs = 900000;

        @Positive(message = "IP rate limit requests per minute must be positive!")
        private int ipRateLimitRequestsPerMinute = 100;

        @Positive(message = "IP rate limit time window seconds must be positive!")
        private int ipRateLimitTimeWindowSeconds = 60;

        @Positive(message = "User quota limit requests per minute must be positive!")
        private int userQuotaLimitRequestsPerMinute = 100;

        @Positive(message = "User quota limit time window seconds must be positive!")
        private int userQuotaLimitTimeWindowSeconds = 60;

        @Positive(message = "Max login attempts must be positive!")
        private int maxLoginAttempts = 5;

        @Positive(message = "Login attempts TTL minutes must be positive!")
        private long loginAttemptsTtlMinutes = 15;

        @Positive(message = "Login lock TTL minutes must be positive!")
        private long loginLockTtlMinutes = 30;
    }
}
