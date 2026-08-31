package com.bookfair.backend.handler;

import org.springframework.stereotype.Component;

import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.model.enums.TaskType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class FailedTaskHandlerRegistry {
    private final Map<TaskType, FailedTaskHandler> handlers = new EnumMap<>(TaskType.class);

    public FailedTaskHandlerRegistry(List<FailedTaskHandler> handlerList) {
        if (handlerList != null) {
            for (FailedTaskHandler handler : handlerList) {
                for (TaskType taskType : handler.getTaskTypes()) {
                    handlers.put(taskType, handler);
                }
            }
        }
    }

    public FailedTaskHandler getHandler(TaskType taskType) {
        if (taskType == null) {
            throw new BusinessException("Task type cannot be null", ErrorCode.INTERNAL_SERVER_ERROR);
        }
        FailedTaskHandler handler = handlers.get(taskType);
        if (handler != null) {
            return handler;
        }
        throw new BusinessException(
            "No handler registered for task type: " + taskType,
            ErrorCode.INTERNAL_SERVER_ERROR
        );
    }
}
