package com.bookfair.backend.handler;

import org.springframework.stereotype.Component;

import com.bookfair.backend.dto.common.EmailTaskPayload;
import com.bookfair.backend.model.FailedTask;
import com.bookfair.backend.model.enums.TaskType;
import com.bookfair.backend.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailConfirmationTaskHandler implements FailedTaskHandler {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    // This handler is genuinely generic — it just deserializes an EmailTaskPayload
    // (to/subject/templateName/variables/qrBase64) and sends it, regardless of which
    // email flow queued it. Every EMAIL_* TaskType is queued with that same payload
    // shape today, so it's listed explicitly here rather than matched by name prefix.
    // A future EMAIL_* type with a different payload shape needs its own handler and
    // must NOT be added to this list.
    private static final List<TaskType> HANDLED_TYPES = List.of(
        TaskType.EMAIL_RESERVATION_CONFIRMATION,
        TaskType.EMAIL_RESERVATION_EXPIRED,
        TaskType.EMAIL_RESERVATION_CANCELLED,
        TaskType.EMAIL_REFUND_PROCESSED,
        TaskType.EMAIL_PASSWORD_RESET,
        TaskType.EMAIL_WELCOME,
        TaskType.EMAIL_VERIFICATION,
        TaskType.EMAIL_SEND_INVITE,
        TaskType.EMAIL_NOTIFICATION
    );

    @Override
    public List<TaskType> getTaskTypes() {
        return HANDLED_TYPES;
    }

    @Override
    public void execute(FailedTask task) {
        try {
            EmailTaskPayload payload = objectMapper.readValue(
                task.getPayloadJson(), EmailTaskPayload.class
            );

            emailService.sendEmailDirect(
                payload.to(),
                payload.subject(),
                payload.templateName(),
                payload.variables(),
                payload.qrBase64()
            );
        } catch (Exception exception) {
            log.error("Failed to execute or deserialize payload for task {}", task.getId(), exception);
            throw new RuntimeException("Failed to execute task: " + exception.getMessage(), exception);
        }
    }
}
