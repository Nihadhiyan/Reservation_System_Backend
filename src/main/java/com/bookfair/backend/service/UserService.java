package com.bookfair.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.reservation.mapper.ReservationMapper;
import com.bookfair.backend.dto.reservation.response.ReservationResponse;
import com.bookfair.backend.dto.user.mapper.UserMapper;
import com.bookfair.backend.dto.user.request.UpdateUserRequest;
import com.bookfair.backend.dto.user.request.UpdateUserRoleRequest;
import com.bookfair.backend.dto.user.response.UserResponse;
import com.bookfair.backend.event.user.UserUpdatedEvent;
import com.bookfair.backend.event.user.UserRoleUpdatedEvent;
import com.bookfair.backend.event.audit.SecurityAuditEvent;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.DuplicateResourceException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ForbiddenException;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.DeletionAudit;
import com.bookfair.backend.model.User;
import com.bookfair.backend.model.enums.SystemRole;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.repository.UserRepository;
import com.bookfair.backend.event.user.UserDeletedEvent;
import com.bookfair.backend.util.SecurityUtils;
import static java.util.Objects.requireNonNull;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

        private final UserRepository userRepository;
        private final ReservationRepository reservationRepository;
        private final UserMapper userMapper;
        private final ReservationMapper reservationMapper;
        private final ApplicationEventPublisher eventPublisher;
        private final SecurityUtils securityUtils;

        // Cache user profile lookups by user ID
        @Cacheable(value = "userProfiles", key = "#userId")
        @Transactional(readOnly = true)
        public UserResponse getUserProfile(UUID userId) {
                requireNonNull(userId, "userId cannot be null");
                User user = userRepository.findByIdAndActiveTrue(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found with ID: " + userId,
                                                ErrorCode.USER_NOT_FOUND));

                return userMapper.toUserResponse(user);
        }


        @Transactional
        public UserResponse updateUser(UUID userId, UpdateUserRequest userUpdateRequest) {
                User user = userRepository.findByIdAndActiveTrue(requireNonNull(userId))
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found with ID: " + userId,
                                                ErrorCode.USER_NOT_FOUND));

                if (userUpdateRequest.username() != null &&
                                !userUpdateRequest.username().equals(user.getUsername()) &&
                                userRepository.existsByUsernameAndActiveTrue(
                                                requireNonNull(userUpdateRequest.username()))) {
                        throw new DuplicateResourceException("Username is already taken.",
                                        ErrorCode.DUPLICATE_USERNAME);
                }

                if (userUpdateRequest.email() != null &&
                                !userUpdateRequest.email().equals(user.getEmail()) &&
                                userRepository.existsByEmailAndActiveTrue(
                                                requireNonNull(userUpdateRequest.email()))) {

                        throw new DuplicateResourceException("That email is already in use by another account.",
                                        ErrorCode.DUPLICATE_EMAIL);
                }

                userMapper.updateUserFromRequest(userUpdateRequest, user);

                User updatedUser = userRepository.save(requireNonNull(user));

                publishUserUpdatedEvent(user);

                return userMapper.toUserResponse(updatedUser);
        }

        @Transactional
        public void deleteUserAsAdmin(UUID userId) {

                User targetUser = userRepository.findByIdAndActiveTrue(requireNonNull(userId))
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found with ID: " + userId,
                                                ErrorCode.USER_NOT_FOUND));

                User requestingUser = getCurrentUser();

                if (requestingUser.getSystemRole() != SystemRole.SUPER_ADMIN) {
                        throw new ForbiddenException("Only SUPER_ADMIN can delete users.", ErrorCode.FORBIDDEN);
                }

                if (requestingUser.getId().equals(userId)) {
                        throw new BusinessException(
                                        "You cannot delete your own admin account.",
                                        ErrorCode.BUSINESS_RULE_VIOLATION);
                }

                if (targetUser.getSystemRole() == SystemRole.SUPER_ADMIN
                                && userRepository.countBySystemRoleAndActiveTrue(SystemRole.SUPER_ADMIN) == 1) {
                        throw new BusinessException("Cannot remove the last administrator", ErrorCode.FORBIDDEN);
                }
                
                softDelete(targetUser);
        }

        @Transactional
        public void deleteMyAccount(UUID userId) {
                User user = userRepository.findByIdAndActiveTrue(requireNonNull(userId))
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found with ID: " + userId,
                                                ErrorCode.USER_NOT_FOUND));

                if (user.getSystemRole() == SystemRole.SUPER_ADMIN) {
                        throw new BusinessException(
                                        "Admin accounts cannot be deactivated",
                                        ErrorCode.FORBIDDEN);
                }

                softDelete(user);
        }

        @Transactional(readOnly = true)
        public List<ReservationResponse> getUserReservations(UUID userId) {
                User user = userRepository.findByIdAndActiveTrue(requireNonNull(userId))
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found with ID: " + userId,
                                                ErrorCode.USER_NOT_FOUND));

                return reservationRepository.findByUserOrderByCreatedAtDesc(user)
                                .stream()
                                .map(reservationMapper::toReservationResponse).toList();
        }


        @Transactional(readOnly = true)
        public Page<UserResponse> getAllUsers(Pageable pageable) {
                return userRepository.findAllByActiveTrue(requireNonNull(pageable))
                                .map(userMapper::toUserResponse);
        }

        @Transactional
        public void setRole(UUID id, UpdateUserRoleRequest updateUserRoleRequest) {
                User targetUser = userRepository.findByIdAndActiveTrue(requireNonNull(id))
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found with ID: " + id,
                                                ErrorCode.USER_NOT_FOUND));

                User requestingUser = getCurrentUser();

                if (targetUser.getSystemRole() == updateUserRoleRequest.role()) {
                        throw new BusinessException(
                                        "User already has this role",
                                        ErrorCode.BUSINESS_RULE_VIOLATION);
                }

                if (requestingUser.getSystemRole() != SystemRole.SUPER_ADMIN) {
                        throw new ForbiddenException("Only SUPER_ADMIN can modify system roles", ErrorCode.FORBIDDEN);
                }

                if (targetUser.getSystemRole() == SystemRole.SUPER_ADMIN
                                && updateUserRoleRequest.role() != SystemRole.SUPER_ADMIN
                                && userRepository.countBySystemRoleAndActiveTrue(SystemRole.SUPER_ADMIN) == 1) {

                        throw new BusinessException("Cannot change the role of last Super admin", ErrorCode.FORBIDDEN);
                }

                SystemRole oldRole = targetUser.getSystemRole();

                targetUser.setSystemRole(updateUserRoleRequest.role());

                User savedUser = userRepository.save(targetUser);

                SystemRole newRole = savedUser.getSystemRole();

                eventPublisher.publishEvent(new UserRoleUpdatedEvent(
                                requireNonNull(savedUser.getId()),
                                requireNonNull(savedUser.getUsername()),
                                requireNonNull(savedUser.getEmail()),
                                requireNonNull(oldRole.name()),
                                requireNonNull(newRole.name())
                                ));
                eventPublisher.publishEvent(new SecurityAuditEvent("SET_ROLE", requestingUser.getUsername(), "Role updated to " + newRole.name() + " for user " + savedUser.getUsername(), Instant.now()));
                publishUserUpdatedEvent(savedUser);
        }

        private User getCurrentUser() {
                UUID currentUserId = securityUtils.getCurrentUserId();

                return userRepository.findById(requireNonNull(currentUserId))
                                .orElseThrow(
                                                () -> new ResourceNotFoundException(
                                                                "Current user not found",
                                                                ErrorCode.USER_NOT_FOUND));
        }

        private void softDelete(User user) {
                user.setActive(false);
                user.setDeletionAudit(
                                new DeletionAudit(
                                                Instant.now(),
                                                requireNonNull(securityUtils.getCurrentUserId())));
                userRepository.save(user);
                eventPublisher.publishEvent(new UserDeletedEvent(requireNonNull(user.getId()),
                                requireNonNull(user.getUsername()), requireNonNull(user.getEmail())));
        }

        private void publishUserUpdatedEvent(User user) {
                eventPublisher.publishEvent(
                                new UserUpdatedEvent(
                                                requireNonNull(user.getId()),
                                                requireNonNull(user.getUsername()),
                                                requireNonNull(user.getEmail())));
        }

}
