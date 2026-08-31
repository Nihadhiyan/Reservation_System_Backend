package com.bookfair.backend.integration.notification;

import java.util.Map;
import java.util.UUID;

import com.bookfair.backend.model.enums.TaskType;

public interface NotificationChannel {
    void send(String recipient, String subject, String template, Map<String, Object> variables, UUID referenceId, TaskType taskType);

    boolean supports(String channelType);
}