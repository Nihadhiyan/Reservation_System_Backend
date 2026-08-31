package com.bookfair.backend.failedtasks;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.handler.FailedTaskHandlerRegistry;
import com.bookfair.backend.model.FailedTask;
import com.bookfair.backend.model.enums.FailedTaskStatus;
import com.bookfair.backend.repository.FailedTaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FailedTaskProcessor {

    private final FailedTaskRepository failedTaskRepository;
    private final FailedTaskHandlerRegistry handlerRegistry;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleTask(FailedTask task) {
        task.setStatus(FailedTaskStatus.RETRYING);
        task.setLastAttemptedAt(Instant.now());
        failedTaskRepository.saveAndFlush(task);

        try {
            handlerRegistry.getHandler(task.getTaskType()).execute(task);

            task.setStatus(FailedTaskStatus.RESOLVED);
            task.setResolvedAt(Instant.now());
            log.info("Failed task {} resolved successfully", task.getId());
        } catch (Exception e) {
            task.setRetryCount(task.getRetryCount() + 1);
            task.setLastError(e.getMessage());

            if (task.getRetryCount() >= task.getMaxRetries()) {
                task.setStatus(FailedTaskStatus.EXHAUSTED);
                log.error("Task {} exhausted all retries: {}", task.getId(), e.getMessage());
            } else {
                task.setStatus(FailedTaskStatus.PENDING);

                long backoffMinutes = (long) Math.pow(2, task.getRetryCount());
                task.setRetryAfter(Instant.now().plusSeconds(backoffMinutes * 60));

                log.warn("Task {} failed, retry {} scheduled in {} minutes",
                        task.getId(), task.getRetryCount(), backoffMinutes);
            }
        }
        failedTaskRepository.save(task);
    }
}
