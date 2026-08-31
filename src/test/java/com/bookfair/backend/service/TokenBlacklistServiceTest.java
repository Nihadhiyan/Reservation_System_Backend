package com.bookfair.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the intentional fail-open (reads) vs fail-closed (writes) split:
 * checkpoint/blacklist WRITES must throw on a Redis outage (a security
 * revocation that silently fails to persist is worse than a loud error),
 * while READS must fail open (an outage must not lock everyone out).
 */
@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void createSecurityCheckpoint_writesCurrentEpochWithTwoHourTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenBlacklistService.createSecurityCheckpoint(userId);

        verify(valueOperations).set(eq("user_security_checkpoint:" + userId), any(), eq(2L), eq(TimeUnit.HOURS));
    }

    @Test
    void createSecurityCheckpoint_failsClosed_whenRedisThrows() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        assertThatThrownBy(() -> tokenBlacklistService.createSecurityCheckpoint(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getSecurityCheckpoint_returnsParsedEpoch() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user_security_checkpoint:" + userId)).thenReturn("1700000000");

        assertThat(tokenBlacklistService.getSecurityCheckpoint(userId)).isEqualTo(1700000000L);
    }

    @Test
    void getSecurityCheckpoint_returnsNull_whenNoCheckpointSet() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(null);

        assertThat(tokenBlacklistService.getSecurityCheckpoint(userId)).isNull();
    }

    @Test
    void getSecurityCheckpoint_returnsNull_onCorruptedValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn("not-a-number");

        assertThat(tokenBlacklistService.getSecurityCheckpoint(userId)).isNull();
    }

    @Test
    void getSecurityCheckpoint_failsOpen_whenRedisThrows() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        assertThat(tokenBlacklistService.getSecurityCheckpoint(userId)).isNull();
    }

    @Test
    void blacklistAccessTokenId_writesWithRemainingLifespan() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenBlacklistService.blacklistAccessTokenId("jti-1", 300);

        verify(valueOperations).set("blacklisted_jti:jti-1", "revoked", 300, TimeUnit.SECONDS);
    }

    @Test
    void blacklistAccessTokenId_skipsWrite_whenAlreadyExpired() {
        tokenBlacklistService.blacklistAccessTokenId("jti-1", 0);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void blacklistAccessTokenId_failsClosed_whenRedisThrows() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        assertThatThrownBy(() -> tokenBlacklistService.blacklistAccessTokenId("jti-1", 300))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isAccessTokenBlacklisted_true_whenKeyPresent() {
        when(redisTemplate.hasKey("blacklisted_jti:jti-1")).thenReturn(true);

        assertThat(tokenBlacklistService.isAccessTokenBlacklisted("jti-1")).isTrue();
    }

    @Test
    void isAccessTokenBlacklisted_failsOpen_whenRedisThrows() {
        when(redisTemplate.hasKey(any())).thenThrow(new RuntimeException("Redis down"));

        assertThat(tokenBlacklistService.isAccessTokenBlacklisted("jti-1")).isFalse();
    }

    @Test
    void isAccessTokenBlacklisted_falseForBlankJti_withoutTouchingRedis() {
        assertThat(tokenBlacklistService.isAccessTokenBlacklisted(" ")).isFalse();
        verifyNoInteractions(redisTemplate);
    }
}
