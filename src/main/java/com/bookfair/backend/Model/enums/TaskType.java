package com.bookfair.backend.model.enums;

public enum TaskType {
        EMAIL_RESERVATION_CONFIRMATION,
        EMAIL_RESERVATION_EXPIRED,
        EMAIL_RESERVATION_CANCELLED,
        EMAIL_REFUND_PROCESSED,
        EMAIL_PASSWORD_RESET,
        EMAIL_WELCOME,
        EMAIL_VERIFICATION,
        EMAIL_SEND_INVITE,
        EMAIL_NOTIFICATION,

        // Payment tasks
        STRIPE_WEBHOOK_PROCESSING,
        STRIPE_REFUND,

        // Generation tasks
        QR_CODE_GENERATION,
        PDF_TICKET_GENERATION,

        // System tasks
        RESERVATION_EXPIRY_CLEANUP,
        ORGANIZATION_DEACTIVATION_CASCADE
}
