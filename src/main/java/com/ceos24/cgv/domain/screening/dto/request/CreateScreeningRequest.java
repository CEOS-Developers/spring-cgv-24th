package com.ceos24.cgv.domain.screening.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "상영 생성 요청 DTO")
public record CreateScreeningRequest(
        @Schema(description = "영화 ID", example = "1") Long movieId,
        @Schema(description = "상영관 ID", example = "1") Long screenId,
        @Schema(description = "상영 시작 시간", example = "2024-05-15T12:00:00") LocalDateTime startTime,
        @Schema(description = "상영 종료 시간", example = "2024-05-15T14:30:00") LocalDateTime endTime) {}
