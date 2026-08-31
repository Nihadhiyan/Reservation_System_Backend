package com.bookfair.backend.service;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupService {
    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 2 * * ?", zone = "UTC")
    @Transactional
    public void reapDeadTokens() {
        try {
            int expiredCount = refreshTokenRepository
                .deleteByExpiryDateBefore(Instant.now());

            Instant revokedCutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            int revokedCount = refreshTokenRepository
                .deleteByRevokedTrueAndCreatedAtBefore(revokedCutoff);

            log.info("Database Reaper: removed {} expired and {} stale revoked refresh tokens",
                expiredCount, revokedCount);

        } catch (Exception e) {
            log.error("Database Reaper failed. Tokens will accumulate until next scheduled run.", e);
        }
    }
}
