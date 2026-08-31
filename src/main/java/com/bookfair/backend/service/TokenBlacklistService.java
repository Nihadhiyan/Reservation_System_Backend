package com.bookfair.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private static final String CHECKPOINT_KEY_PREFIX = "user_security_checkpoint:";
    private static final String BLACKLISTED_KEY_PREFIX = "blacklisted_jti:";

    private final StringRedisTemplate redisTemplate;

    public void createSecurityCheckpoint(UUID userId) {
        Objects.requireNonNull(userId, "User ID cannot be null when creating a security checkpoint");

        String redisKey = CHECKPOINT_KEY_PREFIX + userId;
        long currentEpochSeconds = Instant.now().getEpochSecond();

        try {
            Objects.requireNonNull(redisTemplate.opsForValue()).set(redisKey,
                    requireNonNull(String.valueOf(currentEpochSeconds)), 2,
                    TimeUnit.HOURS);
            log.info("Security checkpoint activated for user [{}]. All JWTs issued before epoch {} are invalidated.",
                    userId, currentEpochSeconds);
        } catch (Exception e) {
            log.error("CRITICAL SECURITY FAILURE: Unable to write security checkpoint to Redis for user [{}]: {}",
                    userId, e.getMessage(), e);
            throw new IllegalStateException(
                    "Failed to activate global security checkpoint due to datastore unavailable", e);
        }
    }

    public Long getSecurityCheckpoint(UUID userId) {
        Objects.requireNonNull(userId, "User ID cannot be null when retrieving security checkpoint");

        String redisKey = CHECKPOINT_KEY_PREFIX + userId;

        try {
            String valueStr = Objects.requireNonNull(redisTemplate.opsForValue()).get(redisKey);
            if (valueStr == null || valueStr.isBlank()) {
                return null;
            }
            return Long.parseLong(valueStr.trim());
        } catch (NumberFormatException e) {
            log.warn("Corrupted security checkpoint timestamp in Redis for key [{}]. Treating as null.", redisKey, e);
            return null;
        } catch (Exception e) {
            log.error(
                    "Redis connection error while fetching security checkpoint for user [{}]. Failing open to prevent outage.",
                    userId, e);
            return null;
        }
    }

    public void blacklistAccessTokenId(String jti, long remainingLifespanSeconds) {
        Objects.requireNonNull(jti, "JTI cannot be null during individual token revocation");
        if (remainingLifespanSeconds <= 0)
            return;

        try {
            Objects.requireNonNull(redisTemplate.opsForValue()).set(
                    BLACKLISTED_KEY_PREFIX + jti,
                    "revoked",
                    remainingLifespanSeconds,
                    TimeUnit.SECONDS);
            log.info("Individual access token [JTI: {}] blacklisted instantly for {} seconds", jti,
                    remainingLifespanSeconds);
        } catch (Exception e) {
            log.error("Failed to blacklist individual JTI [{}]: {}", jti, e.getMessage());
            throw new IllegalStateException("Failed to blacklist individual token due to datastore unavailable", e);
        }
    }

    public boolean isAccessTokenBlacklisted(String jti) {
        if (jti == null || jti.isBlank())
            return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(Objects.requireNonNull(BLACKLISTED_KEY_PREFIX + jti)));
        } catch (Exception e) {
            log.warn("Redis JTI blacklist check failed, failing open: {}", e.getMessage());
            return false;
        }
    }
}
