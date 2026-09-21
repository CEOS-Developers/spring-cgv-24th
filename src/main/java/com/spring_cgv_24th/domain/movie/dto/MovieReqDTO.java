package com.spring_cgv_24th.domain.movie.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class MovieReqDTO {

    private MovieReqDTO() {
    }

    public record CreateMovieDTO(
            @NotBlank(message = "영화 제목은 필수입니다.")
            @Size(max = 200, message = "영화 제목은 200자 이하이어야 합니다.")
            String title,
            String description,

            @NotNull(message = "상영 시간은 필수입니다.")
            @Min(value = 1, message = "상영 시간은 1분 이상이어야 합니다.")
            @Max(value = Short.MAX_VALUE, message = "상영 시간이 너무 깁니다.")
            Integer durationMinutes,

            @NotBlank(message = "관람 등급은 필수입니다.")
            @Size(max = 20, message = "관람 등급은 20자 이하이어야 합니다.")
            String ageRating,
            LocalDate releaseDate,

            @Size(max = 2048, message = "포스터 URL은 2048자 이하이어야 합니다.")
            String posterUrl
    ) {
    }
}
