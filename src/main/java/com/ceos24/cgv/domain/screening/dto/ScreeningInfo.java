package com.ceos24.cgv.domain.screening.dto;

import com.ceos24.cgv.domain.movie.dto.MovieInfo;
import com.ceos24.cgv.domain.theater.dto.ScreenInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "상영 정보 응답 DTO")
public record ScreeningInfo(
        @Schema(description = "영화 정보") MovieInfo movieInfo,
        @Schema(description = "상영관 정보") ScreenInfo screenInfo,
        @Schema(description = "상영 시작 시간", example = "2024-05-15T12:00:00") LocalDateTime startTime,
        @Schema(description = "상영 종료 시간", example = "2024-05-15T14:30:00") LocalDateTime endTime) {}
