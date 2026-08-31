package com.bookfair.backend.dto.genre.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.dto.genre.request.CreateGenreRequest;
import com.bookfair.backend.dto.genre.request.UpdateGenreRequest;
import com.bookfair.backend.dto.genre.response.GenreResponse;
import com.bookfair.backend.model.Genre;

@Mapper(config = GlobalMapperConfig.class)
public interface GenreMapper {
    
    GenreResponse toGenreResponse(Genre genre);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    Genre toGenre(CreateGenreRequest request);

    void updateGenreFromRequest(UpdateGenreRequest request, @MappingTarget Genre genre);
}
