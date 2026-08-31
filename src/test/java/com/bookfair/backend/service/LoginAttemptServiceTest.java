package com.bookfair.backend.service;

import com.bookfair.backend.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private AppProperties appProperties;
    @Mock private AppProperties.Security security;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    private static final String USERNAME = "vendor1";

    @BeforeEach
    void setUp() {
        lenient().when(appProperties.getSecurity()).thenReturn(security);
        lenient().when(security.getMaxLoginAttempts()).thenReturn(5);
        lenient().when(security.getLoginAttemptsTtlMinutes()).thenReturn(15L);
        lenient().when(security.getLoginLockTtlMinutes()).thenReturn(30L);
    }

    @Test
    void recordFailedAttempt_setsExpiry_onFirstAttempt() {
        when(redisTemplate.hasKey("lock:" + USERNAME)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("login_attempts:" + USERNAME)).thenReturn(1L);

        loginAttemptService.recordFailedAttempt(USERNAME);

        verify(redisTemplate).expire("login_attempts:" + USERNAME, 15L, TimeUnit.MINUTES);
        verify(valueOperations, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void recordFailedAttempt_locksAccount_whenMaxAttemptsReached() {
        when(redisTemplate.hasKey("lock:" + USERNAME)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("login_attempts:" + USERNAME)).thenReturn(5L);

        loginAttemptService.recordFailedAttempt(USERNAME);

        verify(valueOperations).set(eq("lock:" + USERNAME), eq("LOCKED"), eq(30L), eq(TimeUnit.MINUTES));
        verify(redisTemplate).delete("login_attempts:" + USERNAME);
    }

    @Test
    void recordFailedAttempt_doesNotIncrement_whenAlreadyLocked() {
        when(redisTemplate.hasKey("lock:" + USERNAME)).thenReturn(true);

        loginAttemptService.recordFailedAttempt(USERNAME);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void recordFailedAttempt_failsOpen_whenRedisThrows() {
        when(redisTemplate.hasKey(any())).thenThrow(new RuntimeException("Redis connection refused"));

        // Must not propagate — login must still succeed/fail based on credentials,
        // not be blocked by a Redis outage.
        loginAttemptService.recordFailedAttempt(USERNAME);
    }

    @Test
    void isLocked_true_whenLockKeyPresent() {
        when(redisTemplate.hasKey("lock:" + USERNAME)).thenReturn(true);

        assertThat(loginAttemptService.isLocked(USERNAME)).isTrue();
    }

    @Test
    void isLocked_failsOpen_whenRedisThrows() {
        when(redisTemplate.hasKey(any())).thenThrow(new RuntimeException("Redis down"));

        assertThat(loginAttemptService.isLocked(USERNAME)).isFalse();
    }

    @Test
    void isLocked_falseForBlankUsername() {
        assertThat(loginAttemptService.isLocked(" ")).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void resetAttempts_deletesBothKeys_whenLocked() {
        when(redisTemplate.hasKey("lock:" + USERNAME)).thenReturn(true);

        loginAttemptService.resetAttempts(USERNAME);

        verify(redisTemplate).delete("lock:" + USERNAME);
        verify(redisTemplate).delete("login_attempts:" + USERNAME);
    }
}
