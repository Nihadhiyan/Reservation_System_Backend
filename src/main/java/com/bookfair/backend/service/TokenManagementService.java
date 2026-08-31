package com.bookfair.backend.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.auth.mapper.AuthMapper;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.UnauthorizedException;
import com.bookfair.backend.model.RefreshToken;
import com.bookfair.backend.model.User;
import com.bookfair.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import static java.util.Objects.requireNonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenManagementService {

    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuthMapper authMapper;

    @Transactional
    public RefreshToken createAndStoreRefreshToken(User user, String tokenJti, long durationMillis,
            String ipAddress, String deviceInfo, String familyId) {
        requireNonNull(user, "User cannot be null when storing refresh token session");
        requireNonNull(tokenJti, "Token string cannot be null when storing refresh token session");
        requireNonNull(familyId, "Family ID cannot be null when storing refresh token session");

        if (durationMillis <= 0) {
            throw new IllegalArgumentException("Token duration must be positive");
        }

        RefreshToken refreshToken = authMapper.toRefreshToken(
                user, tokenJti, Instant.now().plusMillis(durationMillis), ipAddress, deviceInfo, familyId);

        RefreshToken savedToken = refreshTokenRepository.save(requireNonNull(refreshToken));

        log.info("Created granular device session [{}] for user [{}] from IP [{}] on device [{}]",
                savedToken.getId(), user.getId(), ipAddress, deviceInfo);

        return savedToken;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByTokenJti(String jti) {
        requireNonNull(jti, "Token string cannot be null during database session lookup");
        return refreshTokenRepository.findByJti(jti);
    }

    @Transactional
    public void revokeDeviceSessionByFamilyId(String familyId) {
        requireNonNull(familyId, "Family ID cannot be null during family revocation");
        int deleted = refreshTokenRepository.deleteByFamilyId(familyId);
        if (deleted == 0) {
            log.warn("Attempted to revoke non-existent family [{}]", familyId);
        } else {
            log.info("Revoked {} sessions in device family [{}]", deleted, familyId);
        }
    }


    @Transactional
    public void revokeDeviceSessionWithCheckPoint(UUID refreshTokenId, UUID userId) {
        requireNonNull(refreshTokenId, "RefreshToken ID cannot be null during single-device logout");
        requireNonNull(userId, "User ID cannot be null");

        RefreshToken refreshToken = refreshTokenRepository.findById(refreshTokenId)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found", ErrorCode.UNAUTHORIZED));

        if (!refreshToken.getUser().getId().equals(userId)) {
            log.error("SECURITY: Attempted to revoke session [{}] belonging to user [{}] as user [{}]",
                refreshTokenId, refreshToken.getUser().getId(), userId);
            throw new com.bookfair.backend.exception.ForbiddenException("Cannot revoke another user's session", ErrorCode.FORBIDDEN);
        }

        refreshTokenRepository.deleteByFamilyId(refreshToken.getFamilyId());

        tokenBlacklistService.createSecurityCheckpoint(userId);

        log.info("Revoked device family [{}] and issued security checkpoint for user [{}]", refreshToken.getFamilyId(), userId);
    }

    @Transactional
    public void revokeAllUserSessions(UUID userId) {
        requireNonNull(userId, "User ID cannot be null when revoking all user sessions");

        refreshTokenRepository.deleteByUserId(userId);
        tokenBlacklistService.createSecurityCheckpoint(userId);

        log.info("Deleted all persistent database sessions for user [{}]", userId);
    }

    public void storePasswordResetToken(UUID userId, String jti, long timeout, TimeUnit unit) {
        requireNonNull(userId, "userId cannot be null");
        requireNonNull(jti, "jti cannot be null");
        requireNonNull(unit, "unit cannot be null");

        try {
            String key = "password_reset:" + userId;
            requireNonNull(redisTemplate.opsForValue()).set(key, jti, timeout, unit);
        } catch (Exception e) {
            log.error("CRITICAL: Redis unavailable. Cannot store password reset token for user [{}]", userId, e);
            throw new BusinessException("Password reset service is temporarily unavailable. Please try again later.", ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    public void storeEmailVerificationToken(UUID userId, String jti, long timeout, TimeUnit unit) {
        requireNonNull(userId, "userId cannot be null");
        requireNonNull(jti, "jti cannot be null");
        requireNonNull(unit, "unit cannot be null");

        try {
            String key = "email_verify:" + userId;
            requireNonNull(redisTemplate.opsForValue()).set(key, jti, timeout, unit);
        } catch (Exception e) {
            log.error("CRITICAL: Redis unavailable. Cannot store email verification token for user [{}]", userId, e);
            throw new BusinessException("Email verification service is temporarily unavailable. Please try again later.", ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    public void storeInviteToken(String email, String jti, long timeout, TimeUnit unit) {
        requireNonNull(email, "email cannot be null");
        requireNonNull(jti, "jti cannot be null");
        requireNonNull(unit, "unit cannot be null");

        try {
            String key = "org_invite:" + jti;
            requireNonNull(redisTemplate.opsForValue()).set(key, email, timeout, unit);
        } catch (Exception e) {
            log.error("CRITICAL: Redis unavailable. Cannot store org invite token for email [{}]", email, e);
            throw new BusinessException("Organization invite service is temporarily unavailable. Please try again later.", ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    public boolean consumePasswordResetToken(UUID userId, String jti) {
        requireNonNull(userId, "userId cannot be null");
        requireNonNull(jti, "jti cannot be null");

        try {
            String key = "password_reset:" + userId;
            String expectedJti = requireNonNull(redisTemplate.opsForValue()).get(key);

            if (jti.equals(expectedJti)) {
                redisTemplate.delete(requireNonNull(key));
                log.info("Successfully consumed password reset lock for user [{}]", userId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Redis unavailable during password reset token consumption, Failing closed.", e);
            return false;
        }
    }

    public boolean consumeEmailVerificationToken(UUID userId, String jti) {
        requireNonNull(userId, "userId cannot be null");
        requireNonNull(jti, "jti cannot be null");

        try {
            String key = "email_verify:" + userId;
            String expectedJti = requireNonNull(redisTemplate.opsForValue()).get(key);

            if (jti.equals(expectedJti)) {
                redisTemplate.delete(requireNonNull(key));
                log.info("Successfully consumed email verification lock for user [{}]", userId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Redis unavailable during email verification token consumption, Failing closed.", e);
            return false;
        }
    }

    public boolean consumeInviteToken(String jti, String email) {
        requireNonNull(jti, "jti cannot be null");
        requireNonNull(email, "email cannot be null");

        try {
            String key = "org_invite:" + jti;
            String expectedEmail = requireNonNull(redisTemplate.opsForValue()).get(key);

            if (email.equals(expectedEmail)) {
                redisTemplate.delete(requireNonNull(key));
                log.info("Successfully consumed org invite lock for email [{}]", email);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Redis unavailable during org invite token consumption, Failing closed.", e);
            return false;
        }
    }

    @Transactional
    public void saveSession(RefreshToken session) {
        refreshTokenRepository.save(requireNonNull(session, "Session cannot be null"));
    }

}
