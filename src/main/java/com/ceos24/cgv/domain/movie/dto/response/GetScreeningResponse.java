package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.dto.ScreeningMovieInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "상영 스케줄 조회 응답 DTO")
public record GetScreeningResponse(
        @Schema(description = "극장 ID", example = "1") Long theaterId,
        @Schema(description = "영화별 상영 정보 목록") List<ScreeningMovieInfo> movies) {}
