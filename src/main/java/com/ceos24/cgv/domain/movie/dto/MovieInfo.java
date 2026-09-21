package com.ceos24.cgv.domain.movie.dto;

import com.ceos24.cgv.domain.movie.entity.Movie;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "영화 정보 DTO")
public record MovieInfo(
        @Schema(description = "영화 ID", example = "1") Long movieId,
        @Schema(description = "영화 제목", example = "인셉션") String title) {
    public static MovieInfo from(Movie movie) {
        return new MovieInfo(movie.getId(), movie.getTitle());
    }
}
