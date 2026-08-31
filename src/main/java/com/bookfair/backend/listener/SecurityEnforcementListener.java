package com.bookfair.backend.listener;

import java.util.Objects;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bookfair.backend.event.organization.OrganizationDeactivatedEvent;
import com.bookfair.backend.event.user.UserDeletedEvent;
import com.bookfair.backend.event.user.UserAccountLockedEvent;
import com.bookfair.backend.event.user.UserPasswordChangedEvent;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import com.bookfair.backend.service.TokenManagementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityEnforcementListener {

    private final OrganizationMemberRepository memberRepository;
    private final TokenManagementService tokenManagementService;

    // These listeners are AFTER_COMMIT but deliberately NOT @Async: they run synchronously
    // on the original request thread. The primary operation (delete/lock/password-change)
    // has already committed by the time these run, so re-throwing here cannot roll anything
    // back — it only risks surfacing a misleading 500 to the client for a request that
    // actually succeeded, if session revocation hits a transient failure (e.g. Redis blip).
    // Log and move on; revocation failures need alerting, not a corrupted response.

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserDeleted(UserDeletedEvent event) {
        Objects.requireNonNull(event, "Security event cannot be null");
        try {
            handleRevocation(event.userId());
        } catch (Exception ex) {
            log.error("Failed to revoke sessions during user deletion for user {}: {}", event.userId(), ex.getMessage(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserPasswordChanged(UserPasswordChangedEvent event) {
        Objects.requireNonNull(event, "Security event cannot be null");
        try {
            handleRevocation(event.userId());
        } catch (Exception ex) {
            log.error("Failed to revoke sessions during password change for user {}: {}", event.userId(), ex.getMessage(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrganizationDeactivated(OrganizationDeactivatedEvent event) {
        Objects.requireNonNull(event, "Security event cannot be null");

        List<UUID> failedRevocations = new ArrayList<>();

        memberRepository.findByOrganizationId(event.organizationId())
                .forEach(m -> {
                    try {
                        handleRevocation(m.getUser().getId());
                    } catch (Exception ex) {
                        log.error("Failed to revoke session for user {} during organization deactivation: {}",
                                m.getUser().getId(), ex.getMessage());
                        failedRevocations.add(m.getUser().getId());
                    }
                });

        if (!failedRevocations.isEmpty()) {
            log.error("Security enforcement incomplete for organization {} deactivation. "
                    + "Could not revoke sessions for users: {}", event.organizationId(), failedRevocations);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserAccountLocked(UserAccountLockedEvent event) {
        Objects.requireNonNull(event, "Security event cannot be null");
        try {
            handleRevocation(event.userId());
        } catch (Exception ex) {
            log.error("Failed to revoke sessions during account lockout for user {}: {}", event.userId(), ex.getMessage(), ex);
        }
    }

    private void handleRevocation(UUID userId) {
        Objects.requireNonNull(userId, "User ID cannot be null during security event revocation");
        tokenManagementService.revokeAllUserSessions(userId);
    }
}
