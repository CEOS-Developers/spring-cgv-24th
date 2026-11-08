package com.ceos24.cgv.domain.movie.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "상영관 스케줄 정보 DTO")
public record ScreenScheduleInfo(
        @Schema(description = "상영관 ID", example = "1") Long screenId,
        @Schema(description = "상영관 이름", example = "1관") String screenName,
        @Schema(description = "상영 시간표 목록") List<ScheduleTimeInfo> schedules) {}
