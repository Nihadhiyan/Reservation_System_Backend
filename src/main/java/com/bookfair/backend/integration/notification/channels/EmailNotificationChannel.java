package com.bookfair.backend.integration.notification.channels;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import com.bookfair.backend.service.EmailService;
import com.bookfair.backend.integration.notification.NotificationChannel;
import com.bookfair.backend.model.enums.TaskType;

@Service
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private final EmailService emailService;

    @Override
    public void send(String recipient, String subject, String template, Map<String, Object> variables, UUID referenceId, TaskType taskType) {
        String qrCodeBase64 = null;
        if (variables != null && variables.containsKey("qrCodeBase64")) {
            qrCodeBase64 = String.valueOf(variables.getOrDefault("qrCodeBase64", ""));
        }
        emailService.sendEmail(recipient, subject, template, variables, qrCodeBase64, referenceId, taskType);
    }

    @Override
    public boolean supports(String channelType) {
        return "EMAIL".equalsIgnoreCase(channelType);
    }
}
