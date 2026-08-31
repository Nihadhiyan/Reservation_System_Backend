package com.bookfair.backend.model;

import java.time.Instant;
import java.util.UUID;

import com.bookfair.backend.model.enums.FailedTaskStatus;
import com.bookfair.backend.model.enums.TaskType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "failed_tasks", indexes = {
    @Index(name = "idx_failed_task_type", columnList = "task_type"),
    @Index(name = "idx_failed_task_status", columnList = "status"),
    @Index(name = "idx_failed_task_reference", columnList = "reference_id")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
public class FailedTask extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    // What entity this task is about
    // example: the reservationId for a confirmation email
    // or the userId for a welcome email
    @Column(name = "reference_id")
    private UUID referenceId;

    // Human readable context for debugging
    // example: "Send confirmation email to user@email.com for reservation abc-123"
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Full serialized payload needed to retry the task
    // Stored as JSON string — contains everything needed to re-execute
    // example: { "to": "user@email.com", "template": "confirmed", "variables": {...} }
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    // The exception message from the last failure
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Column(name = "last_attempted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant lastAttemptedAt;

    // When to attempt next retry — allows exponential backoff
    @Column(name = "retry_after", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant retryAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FailedTaskStatus status = FailedTaskStatus.PENDING;

    @Column(name = "resolved_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant resolvedAt;
}
