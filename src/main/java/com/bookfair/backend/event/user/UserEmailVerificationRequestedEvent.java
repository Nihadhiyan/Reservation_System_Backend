package com.bookfair.backend.event.user;

import java.util.UUID;

public record UserEmailVerificationRequestedEvent(UUID userId, String username, String verificationLink, String email)
        implements UserEvent {
}
