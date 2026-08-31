package com.bookfair.backend.dto.common;

import java.time.Instant;

import org.springframework.http.HttpStatus;

import com.bookfair.backend.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    Instant timestamp,
    int status,
    String errorMessage,
    ErrorCode code,
    Object details
) {
    public static ErrorResponse build(HttpStatus status, String errorMessage, Object details, ErrorCode code) {
        return new ErrorResponse(
            Instant.now(),
            status.value(),
            errorMessage,
            code,
            details
        );
    }
}
