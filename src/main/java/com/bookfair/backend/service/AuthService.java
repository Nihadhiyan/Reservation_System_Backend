package com.bookfair.backend.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.bookfair.backend.dto.auth.mapper.AuthMapper;
import com.bookfair.backend.dto.auth.request.LoginRequest;
import com.bookfair.backend.dto.auth.request.RefreshTokenRequest;
import com.bookfair.backend.dto.auth.request.RegisterRequest;
import com.bookfair.backend.dto.auth.request.ResetPasswordRequest;
import com.bookfair.backend.dto.auth.request.VerifyEmailRequest;
import com.bookfair.backend.dto.auth.response.AuthResponse;
import com.bookfair.backend.dto.user.request.ChangePasswordRequest;
import com.bookfair.backend.event.user.UserAccountLockedEvent;
import com.bookfair.backend.event.user.UserPasswordChangedEvent;
import com.bookfair.backend.event.user.UserRegisteredEvent;
import com.bookfair.backend.event.user.PasswordResetRequestedEvent;
import com.bookfair.backend.event.user.UserEmailVerificationRequestedEvent;
import com.bookfair.backend.event.user.UserEmailVerifiedEvent;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.DuplicateResourceException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.dto.organization.mapper.OrganizationMapper;
import com.bookfair.backend.dto.organization.request.CreateOrganizationRequest;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.User;
import com.bookfair.backend.model.OrganizationMember;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.model.enums.SystemRole;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import com.bookfair.backend.repository.OrganizationRepository;
import com.bookfair.backend.repository.UserRepository;
import com.bookfair.backend.security.JwtService;
import com.bookfair.backend.model.RefreshToken;
import com.bookfair.backend.util.RequestUtils;

import io.jsonwebtoken.Claims;

import com.bookfair.backend.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import com.bookfair.backend.util.SecurityUtils;
import static java.util.Objects.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;
    private final JwtService jwtService;
    private final TokenManagementService tokenManagementService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ApplicationEventPublisher eventPublisher;
    private final LoginAttemptService loginAttemptService;
    private final OrganizationMapper organizationMapper;
    private final SecurityUtils securityUtils;

    @Transactional
    public AuthResponse register(RegisterRequest registerRequest, HttpServletRequest request) {
        requireNonNull(registerRequest, "Register request cannot be null");
        requireNonNull(request, "HttpServletRequest cannot be null");

        if (userRepository.existsByUsernameAndActiveTrue(registerRequest.username())) {
            throw new DuplicateResourceException("Username is already taken", ErrorCode.DUPLICATE_USERNAME);
        }

        if (userRepository.existsByEmailAndActiveTrue(registerRequest.email())) {
            throw new DuplicateResourceException("Email is already registered", ErrorCode.DUPLICATE_EMAIL);
        }

        User user = authMapper.toUserFromRegisterRequest(registerRequest);

        user.setPassword(passwordEncoder.encode(registerRequest.password()));
        user.setSystemRole(SystemRole.CUSTOMER); // Base role for all regular signups

        User savedUser = null;

        if (registerRequest.organizationDetails() != null) {

            CreateOrganizationRequest orgDto = registerRequest.organizationDetails();

            if (organizationRepository.existsByNameAndActiveTrue(orgDto.name())) {
                throw new DuplicateResourceException(
                        "An organization with the name '" + orgDto.name()
                                + "' already exists. If you work here, please ask your admin for an invite.",
                        ErrorCode.DUPLICATE_ORGANIZATION);
            }

            if (organizationRepository.existsByRegistrationNumberAndActiveTrue(orgDto.registrationNumber())) {
                throw new DuplicateResourceException(
                        "An organization with the same registration number already exists.",
                        ErrorCode.DUPLICATE_REGISTRATION_NUMBER);
            }
            savedUser = userRepository.save(user);

            Organization organization = organizationMapper.toOrganizationFromRegisterRequest(orgDto);

            Organization savedOrganization = organizationRepository.save(requireNonNull(organization));

            OrganizationMember member = organizationMapper.toOrganizationMember(savedUser, savedOrganization,
                    OrganizationRole.ORG_ADMIN);
            memberRepository.save(requireNonNull(member));
        }


        String accessToken = jwtService.generateAccessToken(savedUser);
        String refreshTokenString = jwtService.generateRefreshToken(savedUser);

        String jti = jwtService.extractJti(refreshTokenString);

        // Persist granular session in PostgreSQL
        String ipAddress = RequestUtils.getClientIpAddress(request);
        String deviceInfo = RequestUtils.getDeviceInfo(request);

        String newFamilyId = UUID.randomUUID().toString();

        tokenManagementService.createAndStoreRefreshToken(
            savedUser,
            jti,
            jwtService.getRefreshTokenExpirationTime(),
            ipAddress,
            deviceInfo,
            newFamilyId);

        eventPublisher.publishEvent(
                new UserRegisteredEvent(requireNonNull(savedUser, "User not found").getId(), savedUser.getUsername(), savedUser.getEmail()));

        Long expiresIn = jwtService.getAccessTokenExpirationTime() / 1000; // 1 hour in seconds

        return authMapper.toAuthResponse(savedUser, accessToken, refreshTokenString, expiresIn);

    }

    @Transactional
    public AuthResponse login(LoginRequest loginRequest, HttpServletRequest request) {
        requireNonNull(loginRequest, "Login request cannot be null");
        requireNonNull(request, "HttpServletRequest cannot be null");

        String username = loginRequest.username();

        if (loginAttemptService.isLocked(username)) {
            throw new BusinessException("Account is locked due to too many failed login attempts.",
                    ErrorCode.FORBIDDEN);
        }

        User user = userRepository.findByUsernameAndActiveTrue(username)
                .or(() -> userRepository.findByEmailAndActiveTrue(username))
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password", ErrorCode.UNAUTHORIZED));

        if (!passwordEncoder.matches(loginRequest.password(), user.getPassword())) {
            loginAttemptService.recordFailedAttempt(username);
            if (loginAttemptService.isLocked(username)) {
                eventPublisher
                        .publishEvent(new UserAccountLockedEvent(user.getId(), user.getUsername(), user.getEmail()));
            }
            throw new UnauthorizedException("Invalid username or password", ErrorCode.UNAUTHORIZED);
        }

        loginAttemptService.resetAttempts(username);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenString = jwtService.generateRefreshToken(user);

        String jti = jwtService.extractJti(refreshTokenString);

        // Persist granular login session in database
        String ipAddress = RequestUtils.getClientIpAddress(request);
        String deviceInfo = RequestUtils.getDeviceInfo(request);

        String newFamilyId = UUID.randomUUID().toString();

        tokenManagementService.createAndStoreRefreshToken(
                user,
                jti,
                jwtService.getRefreshTokenExpirationTime(),
                ipAddress,
                deviceInfo,
                newFamilyId);

        Long expiresIn = jwtService.getAccessTokenExpirationTime() / 1000; // 1 hour in seconds

        return authMapper.toAuthResponse(user, accessToken, refreshTokenString, expiresIn);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest refreshTokenRequest, HttpServletRequest request) {
        requireNonNull(refreshTokenRequest, "RefreshTokenRequest cannot be null");
        requireNonNull(request, "HttpServletRequest cannot be null");

        String oldTokenString = refreshTokenRequest.refreshToken();
        requireNonNull(oldTokenString, "Refresh token string cannot be null");

        Claims claims;
        try {
            claims = jwtService.extractAllClaims(oldTokenString);
        } catch (Exception e) {
            throw new UnauthorizedException(
                    "Refresh token is expired or invalid. Please log in again.", ErrorCode.UNAUTHORIZED);
        }

        String tokenJti = claims.getId();

        // Verifying persistent device session against PostgreSQL
        RefreshToken session = tokenManagementService.findByTokenJti(tokenJti)
                .orElseThrow(() -> new UnauthorizedException("Refresh token session is invalid or has been revoked",
                        ErrorCode.UNAUTHORIZED));

        if (session.isRevoked()) {
            log.error("BREACH DETECTED: Attempted reuse of revoked token JTI [{}] for Family [{}]", tokenJti, session.getFamilyId());

            tokenManagementService.revokeDeviceSessionByFamilyId(session.getFamilyId());

            tokenBlacklistService.createSecurityCheckpoint(session.getUser().getId());

            throw new UnauthorizedException("Security breach detected. Please log in again.", ErrorCode.SECURITY_BREACH);
        }

        session.setRevoked(true);
        tokenManagementService.saveSession(session);

        UUID userId = UUID.fromString(claims.getSubject());

        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.USER_NOT_FOUND));

        /*
            * CRITICAL REQUIREMENT - SOFT REVOCATIONS:
            * Why we query fresh database roles: When issuing a new Access Token during a
            * refresh cycle,
            * we DO NOT simply copy claims from the old token or user entity in memory.
            * Instead,
            * calling jwtService.generateAccessToken(user) triggers a live database query
            * against
            * OrganizationMemberRepository.findByUserId(user.getId()).
            * This ensures that if an administrator recently demoted the user or removed
            * them from an
            * organization, those role updates are instantly reflected in the new Access
            * Token payload
            * without requiring the user to execute a hard logout or re-authenticate.
            */
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshTokenString = jwtService.generateRefreshToken(user);

        String jti = jwtService.extractJti(newRefreshTokenString);


        String ipAddress = RequestUtils.getClientIpAddress(request);
        String deviceInfo = RequestUtils.getDeviceInfo(request);

        String inheritedFamilyId = session.getFamilyId();

        tokenManagementService.createAndStoreRefreshToken(
                user,
                jti,
                jwtService.getRefreshTokenExpirationTime(),
                ipAddress,
                deviceInfo,
                inheritedFamilyId);

        Long expiresIn = jwtService.getAccessTokenExpirationTime() / 1000;

        return authMapper.toAuthResponse(user, newAccessToken, newRefreshTokenString, expiresIn);

    }

    @Transactional
    public void logout(String authHeader, RefreshTokenRequest refreshTokenRequest) {

        log.info("User requested logout. Frontend should clear tokens.");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        String token = authHeader.substring(7);

        long remainingTime = jwtService.getRemainingExpirationTime(token) / 1000;

        if (remainingTime > 0) {
            try {
                String jti = jwtService.extractJti(token);
                if (jti != null) {
                    tokenBlacklistService.blacklistAccessTokenId(jti, remainingTime);
                }
                UUID userId = jwtService.extractUserId(token);
                if (userId != null && refreshTokenRequest != null && refreshTokenRequest.refreshToken() != null
                        && !refreshTokenRequest.refreshToken().isBlank()) {

                    String refreshTokenJti = jwtService.extractJti(refreshTokenRequest.refreshToken());

                    RefreshToken refreshToken = tokenManagementService.findByTokenJti(refreshTokenJti)
                        .orElseThrow(() -> new UnauthorizedException("Refresh token not found", ErrorCode.UNAUTHORIZED));

                    tokenManagementService.revokeDeviceSessionByFamilyId(refreshToken.getFamilyId());
                }

                log.info("Token successfully blacklisted and session removed.");
            } catch (Exception e) {
                log.warn("Failed to blacklist token or remove session: {}. Token will expire naturally.",
                        e.getMessage());
            }
        }
    }

    public void forgotPassword(String email) {
        userRepository.findByEmailAndActiveTrue(email).ifPresent(user -> {
            String resetToken = jwtService.generatePasswordResetToken(user);
            String jti = jwtService.extractJti(resetToken);
            tokenManagementService.storePasswordResetToken(
                    user.getId(),
                    jti,
                    15,
                    TimeUnit.MINUTES);

            String resetLink = "https://clausis.com/reset-password?token=" + resetToken;

            eventPublisher.publishEvent(new PasswordResetRequestedEvent(user.getId(), user.getUsername(), resetLink, user.getEmail()));
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        UUID userId = jwtService.extractUserId(request.resetToken());
        String jti = jwtService.extractJti(request.resetToken());

        if (jwtService.isTokenExpired(request.resetToken())) {
            throw new BusinessException("Invalid or expired reset token.", ErrorCode.UNAUTHORIZED);
        }

        String tokenPurpose = jwtService.extractPurpose(request.resetToken());

        if (!"RESET_PASSWORD".equals(tokenPurpose)) {
            throw new BusinessException("Invalid token.", ErrorCode.UNAUTHORIZED);
        }

        if (!tokenManagementService.consumePasswordResetToken(userId, jti)) {
            throw new BusinessException("Token is invalid, expired, or has already been used.", ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.USER_NOT_FOUND));

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        eventPublisher.publishEvent(new UserPasswordChangedEvent(user.getId(), user.getUsername(), user.getEmail()));
    }

    @Transactional
    public void changePassword(ChangePasswordRequest changePasswordRequest) {
        UUID currentUserId = securityUtils.getCurrentUserId();

        User user = userRepository.findByIdAndActiveTrue(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(changePasswordRequest.oldPassword(), user.getPassword())) {
            throw new BusinessException("Incorrect current password", ErrorCode.UNAUTHORIZED);
        }

        if (passwordEncoder.matches(changePasswordRequest.newPassword(), user.getPassword())) {
            throw new BusinessException("New password cannot be the same as the old password",
                    ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        user.setPassword(passwordEncoder.encode(changePasswordRequest.newPassword()));
        userRepository.save(user);

        eventPublisher.publishEvent(new UserPasswordChangedEvent(user.getId(), user.getUsername(), user.getEmail()));

    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest verifyEmailRequest) {

        String verificationToken = verifyEmailRequest.token();

        UUID userId = jwtService.extractUserId(verificationToken);
        String jti = jwtService.extractJti(verificationToken);

        if (jwtService.isTokenExpired(verificationToken)) {
            throw new BusinessException("Invalid or expired verification token.", ErrorCode.UNAUTHORIZED);
        }

        String tokenPurpose = jwtService.extractPurpose(verificationToken);

        if (!"VERIFY_EMAIL".equals(tokenPurpose)) {
            throw new BusinessException("Invalid token.", ErrorCode.UNAUTHORIZED);
        }

        if (!tokenManagementService.consumeEmailVerificationToken(userId, jti)) {
            throw new BusinessException("Token is invalid, expired, or has already been used.", ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new DuplicateResourceException("Email Already Verified.", ErrorCode.UNAUTHORIZED);
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        eventPublisher.publishEvent(new UserEmailVerifiedEvent(user.getId(), user.getUsername(), user.getEmail()));
    }

    public void sendVerificationEmail(String email) {
        userRepository.findByEmailAndActiveTrue(email).ifPresent(user -> {
            String verificationToken = jwtService.generateVerificationToken(user);
            String jti = jwtService.extractJti(verificationToken);
            tokenManagementService.storeEmailVerificationToken(
                    user.getId(),
                    jti,
                    24,
                    TimeUnit.HOURS);

            String verificationLink = "https://clausis.com/verify-email?token=" + verificationToken;

            eventPublisher.publishEvent(
                    new UserEmailVerificationRequestedEvent(user.getId(), user.getUsername(), verificationLink, user.getEmail()));
        });
    }
}
