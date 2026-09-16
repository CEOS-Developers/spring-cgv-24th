package com.ceos24.cgv.domain.movie.dto.request;

import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "영화 생성 및 전체 수정 요청")
public record MovieRequest(

        @NotBlank(message = "영화 제목은 필수입니다.")
        @Size(max = 100, message = "영화 제목은 100자 이하여야 합니다.")
        @Schema(description = "영화 제목", example = "어벤져스")
        String title,

        @NotNull(message = "상영 시간은 필수입니다.")
        @Positive(message = "상영 시간은 0보다 커야 합니다.")
        @Schema(description = "상영 시간(분)", example = "181")
        Integer runningTime,

        @NotBlank(message = "영화 설명은 필수입니다.")
        @Schema(description = "영화 설명", example = "세상을 구하기 위한 마지막 전투")
        String description,

        @NotNull(message = "개봉일은 필수입니다.")
        @Schema(description = "개봉일", example = "2026-09-16")
        LocalDate openDate,

        @Schema(description = "종영일. 미정이면 null", example = "2026-10-16")
        LocalDate endDate,

        @NotNull(message = "상영 상태는 필수입니다.")
        @Schema(description = "상영 상태", example = "SHOWING")
        MovieStatus status

) {
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "종영일은 개봉일보다 빠를 수 없습니다.")
    public boolean isDateRangeValid() {
        return openDate == null
                || endDate == null
                || !endDate.isBefore(openDate);
    }
}