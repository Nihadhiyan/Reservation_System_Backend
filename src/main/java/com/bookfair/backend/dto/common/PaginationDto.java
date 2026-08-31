package com.bookfair.backend.dto.common;

public record PaginationDto(
    Integer page,
    Integer size,
    Long totalElements,
    Integer totalPages
) {}
