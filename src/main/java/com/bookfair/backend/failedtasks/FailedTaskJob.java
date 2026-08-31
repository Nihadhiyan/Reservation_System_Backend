package com.bookfair.backend.failedtasks;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bookfair.backend.model.FailedTask;
import com.bookfair.backend.model.enums.FailedTaskStatus;
import com.bookfair.backend.repository.FailedTaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class FailedTaskJob {

    private final FailedTaskRepository failedTaskRepository;
    private final FailedTaskProcessor failedTaskProcessor;

    @Scheduled(fixedDelay = 60000)
    public void runFailedTaskProcessing() {
        List<FailedTask> pendingTasks = failedTaskRepository.findRetryableTasks(List.of(FailedTaskStatus.PENDING),
                Instant.now());

        for (FailedTask task : pendingTasks) {
            try {
                failedTaskProcessor.processSingleTask(task);
            } catch (Exception e) {
                log.error("Error processing single failed task {}: {}", task.getId(), e.getMessage(), e);
            }
        }
    }
}
