package com.bookfair.backend.event.user;

import java.util.UUID;

public record PasswordResetRequestedEvent(UUID userId, String username, String resetLink, String email) implements UserEvent {
}
