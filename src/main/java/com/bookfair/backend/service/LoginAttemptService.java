package com.bookfair.backend.service;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.bookfair.backend.config.AppProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;

    private final AppProperties appProperties;

    public void recordFailedAttempt(String username) {
        String attemptsKey = "login_attempts:" + username;
        String lockKey = "lock:" + username;

        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
                return;
            }

            Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
            
            if (attempts != null && attempts == 1) {
                redisTemplate.expire(attemptsKey, appProperties.getSecurity().getLoginAttemptsTtlMinutes(), TimeUnit.MINUTES);
            }

            if (attempts != null && attempts >= appProperties.getSecurity().getMaxLoginAttempts()) {
                redisTemplate.opsForValue().set(lockKey, "LOCKED", appProperties.getSecurity().getLoginLockTtlMinutes(), TimeUnit.MINUTES);
                redisTemplate.delete(attemptsKey);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for tracking login attempts, failing open: {}", e.getMessage());
        }
    }

    public boolean isLocked(String username) {
        if (username == null || username.isBlank()) return false;

        String lockKey = "lock:" + username;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
        } catch (Exception e) {
            log.warn("Redis unavailable for lock check, failing open: {}", e.getMessage());
            return false;
        }
    }

    public void resetAttempts(String username) {
        if (username == null || username.isBlank()) return;

        String attemptsKey = "login_attempts:" + username;
        String lockKey = "lock:" + username;

        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
                redisTemplate.delete(lockKey);
            }
            redisTemplate.delete(attemptsKey);
        } catch (Exception e) {
            log.warn("Redis unavailable for resetting login attempts: {}", e.getMessage());
        }
    }
}
