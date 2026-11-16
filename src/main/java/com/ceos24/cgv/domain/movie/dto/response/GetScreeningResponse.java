package com.ceos24.cgv.domain.movie.dto.response;

import com.ceos24.cgv.domain.movie.dto.ScheduleTimeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "상영 스케줄 조회 응답 DTO")
public record GetScreeningResponse(
        @Schema(description = "영화관 ID", example = "1") Long theaterId,
        @Schema(description = "영화별 상영 정보 목록") List<ScreeningMovieInfo> movies) {

    @Schema(description = "상영 영화 정보 DTO")
    public record ScreeningMovieInfo(
            @Schema(description = "영화 ID", example = "1") Long movieId,
            @Schema(description = "영화 제목", example = "인셉션") String movieTitle,
            @Schema(description = "상영관 스케줄 목록") List<ScreenScheduleInfo> screens) {

        @Schema(description = "상영관 스케줄 정보 DTO")
        public record ScreenScheduleInfo(
                @Schema(description = "상영관 ID", example = "1") Long screenId,
                @Schema(description = "상영관 이름", example = "1관") String screenName,
                @Schema(description = "상영 시간표 목록") List<ScheduleTimeInfo> schedules) {}
    }
}
