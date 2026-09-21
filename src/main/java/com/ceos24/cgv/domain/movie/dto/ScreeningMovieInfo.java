package com.ceos24.cgv.domain.movie.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "상영 영화 정보 DTO")
public record ScreeningMovieInfo(
        @Schema(description = "영화 ID", example = "1") Long movieId,
        @Schema(description = "영화 제목", example = "인셉션") String movieTitle,
        @Schema(description = "상영관 스케줄 목록") List<ScreenScheduleInfo> screens) {}
