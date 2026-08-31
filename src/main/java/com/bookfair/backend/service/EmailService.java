package com.bookfair.backend.service;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.bookfair.backend.dto.common.EmailTaskPayload;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.model.FailedTask;
import com.bookfair.backend.model.enums.FailedTaskStatus;
import com.bookfair.backend.model.enums.TaskType;
import com.bookfair.backend.repository.FailedTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import static java.util.Objects.requireNonNull;

import java.time.Instant;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final FailedTaskRepository failedTaskRepository;
    private final ObjectMapper objectMapper;

    @Async("taskExecutor")
    public void sendEmail(String to, String subject, String templateName, Map<String, Object> variables,
            String qrBase64, UUID referenceId, TaskType taskType) {
        requireNonNull(to, "to cannot be null");
        requireNonNull(subject, "subject cannot be null");
        requireNonNull(templateName, "templateName cannot be null");
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);

            boolean hasQrCode = (qrBase64 != null && !qrBase64.isEmpty());
            variables.put("hasQrCode", hasQrCode);

            Context thymeleafContext = new Context();
            thymeleafContext.setVariables(variables);

            String htmlBody = requireNonNull(templateEngine.process("email/" + templateName, thymeleafContext));

            helper.setText(htmlBody, true);

            if (hasQrCode) {
                byte[] decodedImg = requireNonNull(Base64.getDecoder().decode(qrBase64));
                helper.addInline("qrCode", new ByteArrayResource(decodedImg), "image/png");
            }

            mailSender.send(message);
            log.info("Email successfully sent to {}", to);

        } catch (Exception e) {

            log.error("CRITICAL: Failed to send email to {}. Reason: {}", to, e.getMessage(), e);

            EmailTaskPayload payload = new EmailTaskPayload(
                to,
                subject,
                templateName,
                variables,
                qrBase64
            );

            FailedTask failedTask = new FailedTask();

            failedTask.setTaskType(taskType != null ? taskType : TaskType.EMAIL_NOTIFICATION);
            failedTask.setReferenceId(referenceId);
            failedTask.setDescription("Send " + templateName + " to " + to);

            try {
                failedTask.setPayloadJson(objectMapper.writeValueAsString(payload));
            } catch (Exception ex) {
                log.error("CRITICAL: Failed to serialize payload for task type {}", taskType);
                failedTask.setPayloadJson("{}");
            }

            failedTask.setLastError(e.getMessage());
            failedTask.setRetryAfter(Instant.now().plusSeconds(120)); // first retry in 2 min
            failedTask.setStatus(FailedTaskStatus.PENDING);

            failedTaskRepository.save(failedTask);

        }
    }


    public void sendEmailDirect(String to, String subject, String templateName, Map<String, Object> variables,
            String qrBase64) {
            requireNonNull(to, "to cannot be null");
            requireNonNull(subject, "subject cannot be null");
            requireNonNull(templateName, "templateName cannot be null");
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);

            boolean hasQrCode = (qrBase64 != null && !qrBase64.isEmpty());
            variables.put("hasQrCode", hasQrCode);

            Context thymeleafContext = new Context();
            thymeleafContext.setVariables(variables);

            String htmlBody = requireNonNull(templateEngine.process("email/" + templateName, thymeleafContext));

            helper.setText(htmlBody, true);

            if (hasQrCode) {
                byte[] decodedImg = requireNonNull(Base64.getDecoder().decode(qrBase64));
                helper.addInline("qrCode", new ByteArrayResource(decodedImg), "image/png");
            }

            mailSender.send(message);
            log.info("Email successfully sent to {}", to);

        } catch (Exception e) {

            log.error("CRITICAL: Failed to send email to {}. Reason: {}", to, e.getMessage(), e);
            throw new BusinessException("Failed to send email directly to " + to + ": " + e.getMessage(), e, ErrorCode.INTERNAL_SERVER_ERROR);

        }
    }
}
