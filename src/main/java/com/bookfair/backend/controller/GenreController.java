package com.bookfair.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookfair.backend.dto.common.ApiResponseDto;
import com.bookfair.backend.dto.genre.request.CreateGenreRequest;
import com.bookfair.backend.dto.genre.response.GenreResponse;
import com.bookfair.backend.service.GenreService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORG_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseDto<List<GenreResponse>>> getAllGenres() {
        List<GenreResponse> data = genreService.getAllGenres();
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Genres fetched successfully", data, Instant.now()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponseDto<GenreResponse>> createGenre(@Valid @RequestBody CreateGenreRequest request) {
        GenreResponse data = genreService.createGenre(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDto<>(true, "Genre created successfully", data, Instant.now()));
    }

    @DeleteMapping("/{genreId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponseDto<Void>> deleteGenre(@PathVariable UUID genreId) {
        genreService.deleteGenre(genreId);
        return ResponseEntity.ok(new ApiResponseDto<>(true, "Genre deleted successfully", null, Instant.now()));
    }
}
