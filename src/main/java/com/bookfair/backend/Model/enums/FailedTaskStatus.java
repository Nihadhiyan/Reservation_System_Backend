package com.bookfair.backend.model.enums;

public enum FailedTaskStatus {
        PENDING,    // waiting to be retried
        RETRYING,   // currently being retried
        RESOLVED,   // succeeded on retry
        EXHAUSTED,  // max retries reached, needs manual intervention
        CANCELLED   // manually cancelled by admin
}
