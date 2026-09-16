package com.ceos24.cgv.domain.movie.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "상영 시간 정보 DTO")
public record ScheduleTimeInfo(
        @Schema(description = "상영 ID", example = "1") Long screeningId,
        @Schema(description = "시작 시간", example = "2024-11-20T10:00:00") LocalDateTime startTime,
        @Schema(description = "종료 시간", example = "2024-11-20T12:30:00") LocalDateTime endTime,
        @Schema(description = "잔여 좌석 수", example = "120") Long availableSeats) {}
