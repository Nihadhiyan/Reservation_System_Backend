package com.bookfair.backend.event.reservation;

import java.util.UUID;

public record ReservationCancelledByAdminEvent(UUID reservationId, String username, String email, String eventName, String reason) {
}
