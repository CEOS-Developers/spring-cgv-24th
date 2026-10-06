package com.ceos24.spring_cgv.domain.movie.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "영화관 수정 요청")
public record CinemaUpdateRequest(

        @Schema(description = "영화관 이름", example = "CGV 강남")
        @NotBlank(message = "영화관 이름은 필수입니다.")
        @Size(max = 50, message = "영화관 이름은 50자를 넘을 수 없습니다.")
        String name,

        @Schema(description = "지역", example = "강남")
        @NotBlank(message = "지역은 필수입니다.")
        @Size(max = 50, message = "지역은 50자를 넘을 수 없습니다.")
        String region,

        @Schema(description = "주소", example = "서울시 강남구 테헤란로 123")
        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 200, message = "주소는 200자를 넘을 수 없습니다.")
        String address
) {
}
