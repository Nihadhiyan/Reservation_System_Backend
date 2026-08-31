package com.bookfair.backend.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.bookfair.backend.integration.notification.NotificationChannel;
import com.bookfair.backend.model.enums.TaskType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<NotificationChannel> channels;

    public void notify(String recipient, String subject, String template, Map<String, Object> vars) {
        notify(recipient, subject, template, vars, null, TaskType.EMAIL_NOTIFICATION, "EMAIL");
    }

    public void notify(String recipient, String subject, String template, Map<String, Object> vars, String requestedChannelType) {
        notify(recipient, subject, template, vars, null, TaskType.EMAIL_NOTIFICATION, requestedChannelType);
    }

    public void notify(String recipient, String subject, String template, Map<String, Object> vars, UUID referenceId, TaskType taskType) {
        notify(recipient, subject, template, vars, referenceId, taskType, "EMAIL");
    }

    public void notify(String recipient, String subject, String template, Map<String, Object> vars, UUID referenceId, TaskType taskType, String requestedChannelType) {
        // Iterate through all injected channels and trigger send().
        // this can be filtered by channel.supports("EMAIL") or "SMS"
        for (NotificationChannel channel : channels) {
            if (channel.supports(requestedChannelType)) {
                channel.send(recipient, subject, template, vars, referenceId, taskType);
            }
        }
    }
}