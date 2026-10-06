package com.ceos24.cgv.domain.movie.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "영화 생성 요청 DTO")
public record CreateMovieRequest(@Schema(description = "영화 제목", example = "인셉션") String title) {}
