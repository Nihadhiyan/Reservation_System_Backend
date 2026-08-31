package com.bookfair.backend.handler;

import com.bookfair.backend.model.FailedTask;
import com.bookfair.backend.model.enums.TaskType;

import java.util.List;

public interface FailedTaskHandler {
    // Every TaskType this handler can process. A handler that only understands one
    // payload shape must list only the TaskTypes that actually use that shape —
    // no implicit fallback routing happens for anything not listed here.
    List<TaskType> getTaskTypes();
    void execute(FailedTask task);
}
