package com.bookfair.backend.dto.common;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponseDto<T>(
    boolean success,
    String message,
    T data,
    Instant timestamp
) {}
