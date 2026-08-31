package com.bookfair.backend.listener;

import java.util.List;
import java.util.Map;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.bookfair.backend.event.organization.OrganizationDeactivatedEvent;
import com.bookfair.backend.event.organization.OrganizationInviteSentEvent;
import com.bookfair.backend.event.organization.UserJoinedOrganizationEvent;
import com.bookfair.backend.event.user.UserAccountLockedEvent;
import com.bookfair.backend.event.user.UserDeletedEvent;
import com.bookfair.backend.event.user.UserPasswordChangedEvent;
import com.bookfair.backend.event.user.UserRegisteredEvent;
import com.bookfair.backend.event.user.PasswordResetRequestedEvent;
import com.bookfair.backend.event.user.UserEmailVerificationRequestedEvent;
import com.bookfair.backend.event.user.UserRoleUpdatedEvent;
import com.bookfair.backend.event.user.UserEmailVerifiedEvent;
import com.bookfair.backend.event.reservation.ReservationRequestReceivedEvent;
import com.bookfair.backend.event.reservation.ReservationConfirmedEvent;
import com.bookfair.backend.event.reservation.ReservationRefundPendingEvent;
import com.bookfair.backend.event.reservation.ReservationRefundedEvent;
import com.bookfair.backend.event.reservation.ReservationExpiredEvent;
import com.bookfair.backend.model.User;
import com.bookfair.backend.model.enums.SystemRole;
import com.bookfair.backend.model.OrganizationMember;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.repository.UserRepository;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import com.bookfair.backend.repository.OrganizationRepository;
import com.bookfair.backend.repository.VenueRepository;
import com.bookfair.backend.event.user.UserUpdatedEvent;
import com.bookfair.backend.event.cache.OrganizationUpdatedEvent;
import com.bookfair.backend.event.organization.OrganizationCreatedEvent;
import com.bookfair.backend.event.cache.VenueCreatedEvent;
import com.bookfair.backend.event.cache.VenueUpdatedEvent;
import com.bookfair.backend.event.hierarchy.VenueDeactivatedEvent;
import com.bookfair.backend.event.hierarchy.EventDeactivatedEvent;
import com.bookfair.backend.event.reservation.ReservationCancelledByAdminEvent;
import com.bookfair.backend.model.enums.TaskType;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.service.NotificationService;

import static java.util.Objects.requireNonNull;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onUserAccountLocked(UserAccountLockedEvent e) {
        requireNonNull(e, "Event cannot be null");
        Map<String, Object> vars = Map.of(
                "userName", e.username(),
                "supportEmail", "[EMAIL_ADDRESS]");
        // Notify User
        notificationService.notify(e.email(), "Security Alert: Account Locked", "account_locked", vars);

        // Notify Admins
        List<User> admins = userRepository.findBySystemRole(SystemRole.SUPER_ADMIN);
        for (User admin : admins) {
            Map<String, Object> adminVars = Map.of(
                    "userName", admin.getUsername(),
                    "alertMessage", "A user account has been locked due to multiple failed login attempts.",
                    "affectedUser", e.username());
            notificationService.notify(admin.getEmail(), "Admin Alert: User Account Locked", "admin_alert",
                    adminVars);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onUserPasswordChanged(UserPasswordChangedEvent e) {
        requireNonNull(e, "Event cannot be null");
        Map<String, Object> vars = Map.of("userName", e.username());
        notificationService.notify(e.email(), "Password Changed", "password_changed", vars);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onUserRegistered(UserRegisteredEvent e) {
        requireNonNull(e, "Event cannot be null");
        Map<String, Object> vars = Map.of("userName", e.username());
        notificationService.notify(e.email(), "Welcome!", "welcome", vars, e.userId(), TaskType.EMAIL_WELCOME);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onOrganizationDeactivated(OrganizationDeactivatedEvent e) {
        requireNonNull(e, "Event cannot be null");
        List<OrganizationMember> members = memberRepository.findByOrganizationId(e.organizationId());
        for (OrganizationMember member : members) {
            User employee = member.getUser();
            Map<String, Object> vars = Map.of(
                    "userName", employee.getUsername(),
                    "orgName",
                    member.getOrganization() != null ? member.getOrganization().getName()
                            : "Your Organization",
                    "deactivationDate", java.time.LocalDate.now().toString());
            notificationService.notify(employee.getEmail(), "Organization Deactivated", "org_deactivated",
                    vars);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onPasswordResetRequested(PasswordResetRequestedEvent e) {
        requireNonNull(e, "Event cannot be null");
        Map<String, Object> vars = Map.of(
                "userName", e.username(),
                "resetLink", e.resetLink());
        notificationService.notify(e.email(), "Password Reset Request", "password_reset_template",
                vars, e.userId(), TaskType.EMAIL_PASSWORD_RESET);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onUserEmailVerificationRequested(UserEmailVerificationRequestedEvent e) {
        requireNonNull(e, "Event cannot be null");
        Map<String, Object> vars = Map.of(
                "userName", e.username(),
                "verificationLink", e.verificationLink());
        notificationService.notify(e.email(), "Verify Your Email", "email_verification_template",
                vars, e.userId(), TaskType.EMAIL_VERIFICATION);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onReservationRequestReceived(ReservationRequestReceivedEvent e) {
        requireNonNull(e, "Event cannot be null");
        notificationService.notify(e.email(), "Reservation Request Received", "pending",
                Map.of("userName", e.username(), "eventName", e.eventName()), e.reservationId(), TaskType.EMAIL_RESERVATION_CONFIRMATION);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onReservationConfirmed(ReservationConfirmedEvent e) {
        requireNonNull(e, "Event cannot be null");
        Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("userName", e.username());
        vars.put("eventName", e.eventName());
        if (e.qrCodeBase64() != null) {
            vars.put("qrCodeBase64", e.qrCodeBase64());
        }
        notificationService.notify(e.email(), "Reservation Confirmed - Your Ticket", "confirmed", vars, e.reservationId(), TaskType.EMAIL_RESERVATION_CONFIRMATION);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onReservationRefundPending(ReservationRefundPendingEvent e) {
        requireNonNull(e, "Event cannot be null");
        notificationService.notify(e.email(), "Refund Request Received", "refund_pending",
                Map.of("userName", e.username(), "eventName", e.eventName()), e.reservationId(), TaskType.EMAIL_REFUND_PROCESSED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onReservationRefunded(ReservationRefundedEvent e) {
        requireNonNull(e, "Event cannot be null");
        notificationService.notify(e.email(), "Refund Processed Successfully", "refunded",
                Map.of("userName", e.username(), "eventName", e.eventName()), e.reservationId(), TaskType.EMAIL_REFUND_PROCESSED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onReservationExpired(ReservationExpiredEvent e) {
        requireNonNull(e, "Event cannot be null");
        notificationService.notify(e.email(), "Reservation Expired", "expired",
                Map.of("userName", e.username(), "eventName", e.eventName()), e.reservationId(), TaskType.EMAIL_RESERVATION_EXPIRED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onUserRoleUpdated(UserRoleUpdatedEvent e) {
        requireNonNull(e, "Event cannot be null");
        notificationService.notify(e.email(), "Role Updated", "role_updated",
                Map.of("userName", e.username(), "oldRole", e.oldRole(), "newRole", e.newRole()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onUserEmailVerified(UserEmailVerifiedEvent e) {
        requireNonNull(e, "Event cannot be null");
        Map<String, Object> vars = Map.of(
                "userName", e.username());
        notificationService.notify(e.email(), "Email Verified", "email_verified", vars, e.userId(), TaskType.EMAIL_VERIFICATION);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onUserUpdated(UserUpdatedEvent e) {
        requireNonNull(e, "Event cannot be null");
        Map<String, Object> vars = Map.of(
                "userName", e.username(),
                "entityType", "User",
                "alertMessage", "Your user profile details have been updated.");
        notificationService.notify(e.email(), "Profile Details Updated", "update_alert", vars);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onUserDeleted(UserDeletedEvent e) {
        requireNonNull(e, "Event cannot be null");
        Map<String, Object> vars = Map.of(
                "userName", e.username(),
                "entityType", "User",
                "alertMessage", "Your user profile has been deleted. Contact the administrator for more information.");
        notificationService.notify(e.email(), "Profile Details Deleted", "delete_alert", vars);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onOrganizationUpdated(OrganizationUpdatedEvent e) {
        requireNonNull(e, "Event cannot be null");
        organizationRepository.findById(requireNonNull(e.organizationId(), "Organization ID cannot be null"))
                .ifPresent(org -> {
                    List<OrganizationMember> members = memberRepository
                            .findByOrganizationId(requireNonNull(org.getId()));
                    for (OrganizationMember member : members) {
                        if (member.getRole() == OrganizationRole.ORG_ADMIN && member.getUser() != null) {
                            User admin = member.getUser();
                            Map<String, Object> vars = Map.of(
                                    "userName", admin.getUsername(),
                                    "alertMessage",
                                    "Your organization have been updated: "
                                            + org.getName());
                            notificationService.notify(admin.getEmail(), "Organization Profile Updated",
                                    "org_updated",
                                    vars);
                        }
                    }
                });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onOrganizationCreated(OrganizationCreatedEvent e) {
        requireNonNull(e, "Event cannot be null");
        organizationRepository.findById(requireNonNull(e.organizationId(), "Organization ID cannot be null"))
                .ifPresent(org -> {
                    List<User> admins = userRepository.findBySystemRole(SystemRole.SUPER_ADMIN);
                    for (User admin : admins) {
                        Map<String, Object> vars = Map.of(
                                "userName", admin.getUsername(),
                                "orgName", org.getName(),
                                "alertMessage", "A new organization has been registered on the platform.");
                        notificationService.notify(admin.getEmail(), "New Organization Registered", "org_created",
                                vars);
                    }
                });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onVenueCreated(VenueCreatedEvent e) {
        requireNonNull(e, "Event cannot be null");
        venueRepository.findById(requireNonNull(e.venueId(), "Venue ID cannot be null")).ifPresent(venue -> {
            Organization owner = venue.getOwner();
            if (owner != null) {
                List<OrganizationMember> members = memberRepository.findByOrganizationId(owner.getId());
                for (OrganizationMember member : members) {
                    if (member.getRole() == OrganizationRole.ORG_ADMIN && member.getUser() != null) {
                        User admin = member.getUser();
                        Map<String, Object> vars = Map.of(
                                "userName", admin.getUsername(),
                                "entityType", "Venue",
                                "alertMessage", "A new venue has been created: " + venue.getName());
                        notificationService.notify(admin.getEmail(), "Venue Created", "create_alert",
                                vars);
                    }
                }
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onVenueUpdated(VenueUpdatedEvent e) {
        requireNonNull(e, "Event cannot be null");
        venueRepository.findById(requireNonNull(e.venueId(), "Venue ID cannot be null")).ifPresent(venue -> {
            Organization owner = venue.getOwner();
            if (owner != null) {
                List<OrganizationMember> members = memberRepository.findByOrganizationId(owner.getId());
                for (OrganizationMember member : members) {
                    if (member.getRole() == OrganizationRole.ORG_ADMIN && member.getUser() != null) {
                        User admin = member.getUser();
                        Map<String, Object> vars = Map.of(
                                "userName", admin.getUsername(),
                                "entityType", "Venue",
                                "alertMessage", "Your venue details have been updated: " + venue.getName());
                        notificationService.notify(admin.getEmail(), "Venue Details Updated", "update_alert",
                                vars);
                    }
                }
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onVenueDeactivated(VenueDeactivatedEvent e) {
        requireNonNull(e, "Event cannot be null");
        venueRepository.findById(requireNonNull(e.venueId(), "Venue ID cannot be null")).ifPresent(venue -> {
            Organization owner = venue.getOwner();
            if (owner != null) {
                List<OrganizationMember> members = memberRepository.findByOrganizationId(owner.getId());
                for (OrganizationMember member : members) {
                    if (member.getRole() == OrganizationRole.ORG_ADMIN && member.getUser() != null) {
                        User admin = member.getUser();
                        Map<String, Object> vars = Map.of(
                                "userName", admin.getUsername(),
                                "entityType", "Venue",
                                "alertMessage", "Your venue has been deactivated: " + venue.getName());
                        notificationService.notify(admin.getEmail(), "Venue Deactivated Notice", "venue_deactivated", vars);
                    }
                }
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onEventDeactivated(EventDeactivatedEvent e) {
        requireNonNull(e, "Event cannot be null");
        eventRepository.findById(requireNonNull(e.eventId(), "Event ID cannot be null")).ifPresent(ev -> {
            Organization organizer = ev.getOrganizer();
            if (organizer != null) {
                List<OrganizationMember> members = memberRepository.findByOrganizationId(organizer.getId());
                for (OrganizationMember member : members) {
                    if (member.getRole() == OrganizationRole.ORG_ADMIN && member.getUser() != null) {
                        User admin = member.getUser();
                        Map<String, Object> vars = Map.of(
                                "userName", admin.getUsername(),
                                "eventName", ev.getName(),
                                "alertMessage", "Your event '" + ev.getName() + "' has been deactivated due to administrative action or venue closure.");
                        notificationService.notify(admin.getEmail(), "Event Deactivated Notice", "event_deactivated", vars);
                    }
                }
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onReservationCancelledByAdmin(ReservationCancelledByAdminEvent e) {
        requireNonNull(e, "Event cannot be null");
        Map<String, Object> vars = Map.of(
                "userName", e.username(),
                "eventName", e.eventName(),
                "reservationId", e.reservationId().toString(),
                "reason", e.reason() != null ? e.reason() : "Administrative closure",
                "refundMessage", "Please note that if you made a payment for this reservation, a full refund is currently being processed to your original payment method.");
        notificationService.notify(e.email(), "Reservation Cancellation Notice - Refund Initiated", "reservation_cancelled_admin", vars, e.reservationId(), TaskType.EMAIL_RESERVATION_CANCELLED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onOrganizationInviteSent(OrganizationInviteSentEvent event) {
        notificationService.notify(
            event.inviteeEmail(),
            "You have been invited to join " + event.orgName(),
            "org_invite",
            Map.of(
                "orgName", event.orgName(),
                "role", event.assignedRole().name(),
                "acceptLink", event.acceptLink()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("taskExecutor")
    public void onUserJoinedOrganization(UserJoinedOrganizationEvent event) {
        List<OrganizationMember> admins = memberRepository
            .findByOrganizationIdAndRole(event.orgId(), OrganizationRole.ORG_ADMIN);
        for (OrganizationMember admin : admins) {
            if (admin.getUser() != null) {
                notificationService.notify(
                    admin.getUser().getEmail(),
                    "New member joined " + event.orgName(),
                    "member_joined",
                    Map.of(
                        "adminName", admin.getUser().getUsername(),
                        "newMemberName", event.username(),
                        "orgName", event.orgName(),
                        "role", event.assignedRole().name()));
            }
        }
    }
}