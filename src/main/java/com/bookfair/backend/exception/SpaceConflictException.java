package com.bookfair.backend.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

public class SpaceConflictException extends BaseException {

    private final List<ConflictDetail> conflicts;

    public SpaceConflictException(String message, ErrorCode errorCode, List<ConflictDetail> conflicts) {
        super(message, errorCode, HttpStatus.CONFLICT);
        this.conflicts = conflicts;
    }

    public SpaceConflictException(String message, Throwable cause, ErrorCode errorCode, List<ConflictDetail> conflicts) {
        super(message, cause, errorCode, HttpStatus.CONFLICT);
        this.conflicts = conflicts;
    }

    public List<ConflictDetail> getConflicts() {
        return conflicts;
    }
}
