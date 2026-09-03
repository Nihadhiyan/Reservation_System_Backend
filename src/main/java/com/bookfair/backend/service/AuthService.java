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
import com.bookfair.backend.security.keycloak.KeycloakIdentityService;
import com.bookfair.backend.util.SecurityUtils;

import com.bookfair.backend.exception.UnauthorizedException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.HttpServletRequest;
import static java.util.Objects.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Credential verification and session token issuance now live in Keycloak
 * (see KeycloakIdentityService) — this class stays responsible for
 * everything Keycloak has no equivalent for: local business-data creation on
 * registration, brute-force lockout (LoginAttemptService, checked BEFORE
 * Keycloak is ever asked to verify a password), and the single-purpose
 * password-reset/email-verification tokens (JwtService). Registration and
 * password changes keep a local BCrypt hash in sync purely so
 * changePassword's "confirm your current password" check keeps working
 * without a round trip to Keycloak — Keycloak's copy is always the one that
 * actually gates login.
 */
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
    private final KeycloakIdentityService keycloakIdentityService;
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

        CreateOrganizationRequest orgDto = registerRequest.organizationDetails();
        if (orgDto != null) {
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
        }

        User user = authMapper.toUserFromRegisterRequest(registerRequest);
        user.setPassword(passwordEncoder.encode(registerRequest.password()));
        user.setSystemRole(SystemRole.CUSTOMER); // Base role for all regular signups

        User savedUser = userRepository.save(user);

        if (orgDto != null) {
            Organization organization = organizationMapper.toOrganizationFromRegisterRequest(orgDto);
            Organization savedOrganization = organizationRepository.save(requireNonNull(organization));

            OrganizationMember member = organizationMapper.toOrganizationMember(savedUser, savedOrganization,
                    OrganizationRole.ORG_ADMIN);
            memberRepository.save(requireNonNull(member));
        }

        keycloakIdentityService.createUser(savedUser.getUsername(), savedUser.getEmail(), registerRequest.password());

        eventPublisher.publishEvent(
                new UserRegisteredEvent(savedUser.getId(), savedUser.getUsername(), savedUser.getEmail()));

        KeycloakIdentityService.TokenResponse tokens = keycloakIdentityService.passwordGrant(
                savedUser.getUsername(), registerRequest.password());

        return authMapper.toAuthResponse(savedUser, tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
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

        KeycloakIdentityService.TokenResponse tokens;
        try {
            tokens = keycloakIdentityService.passwordGrant(user.getUsername(), loginRequest.password());
        } catch (UnauthorizedException e) {
            loginAttemptService.recordFailedAttempt(username);
            if (loginAttemptService.isLocked(username)) {
                eventPublisher
                        .publishEvent(new UserAccountLockedEvent(user.getId(), user.getUsername(), user.getEmail()));
            }
            throw e;
        }

        loginAttemptService.resetAttempts(username);

        return authMapper.toAuthResponse(user, tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest refreshTokenRequest, HttpServletRequest request) {
        requireNonNull(refreshTokenRequest, "RefreshTokenRequest cannot be null");
        requireNonNull(request, "HttpServletRequest cannot be null");

        String oldTokenString = refreshTokenRequest.refreshToken();
        requireNonNull(oldTokenString, "Refresh token string cannot be null");

        KeycloakIdentityService.TokenResponse tokens = keycloakIdentityService.refreshGrant(oldTokenString);

        String email = extractEmailClaimUnverified(tokens.accessToken());
        User user = userRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.USER_NOT_FOUND));

        return authMapper.toAuthResponse(user, tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }

    @Transactional
    public void logout(String authHeader, RefreshTokenRequest refreshTokenRequest) {

        log.info("User requested logout.");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            blacklistRemainingLifetime(authHeader.substring(7));
        }

        if (refreshTokenRequest != null && refreshTokenRequest.refreshToken() != null
                && !refreshTokenRequest.refreshToken().isBlank()) {
            keycloakIdentityService.logout(refreshTokenRequest.refreshToken());
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
        keycloakIdentityService.updateUserPassword(user.getUsername(), request.newPassword());

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
        keycloakIdentityService.updateUserPassword(user.getUsername(), changePasswordRequest.newPassword());

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

    private void blacklistRemainingLifetime(String accessToken) {
        try {
            JWTClaimsSet claims = JWTParser.parse(accessToken).getJWTClaimsSet();
            String jti = claims.getJWTID();
            if (jti == null || claims.getExpirationTime() == null) {
                return;
            }
            long remainingSeconds = (claims.getExpirationTime().getTime() - System.currentTimeMillis()) / 1000;
            if (remainingSeconds > 0) {
                tokenBlacklistService.blacklistAccessTokenId(jti, remainingSeconds);
            }
        } catch (Exception e) {
            log.warn("Failed to parse access token during logout for blacklisting: {}. Token will expire naturally.",
                    e.getMessage());
        }
    }

    private String extractEmailClaimUnverified(String accessToken) {
        try {
            JWTClaimsSet claims = JWTParser.parse(accessToken).getJWTClaimsSet();
            return claims.getStringClaim("email");
        } catch (Exception e) {
            throw new BusinessException("Received a malformed token from the identity provider",
                    ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
