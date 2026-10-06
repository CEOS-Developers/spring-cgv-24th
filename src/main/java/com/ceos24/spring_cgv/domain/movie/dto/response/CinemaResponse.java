package com.ceos24.spring_cgv.domain.movie.dto.response;

import com.ceos24.spring_cgv.domain.movie.entity.Cinema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "영화관 응답")
public record CinemaResponse(

        @Schema(description = "영화관 ID", example = "1")
        Long id,

        @Schema(description = "영화관 이름", example = "CGV 강남")
        String name,

        @Schema(description = "지역", example = "강남")
        String region,

        @Schema(description = "주소", example = "서울시 강남구 테헤란로 123")
        String address
) {

    public static CinemaResponse from(Cinema cinema) {
        return new CinemaResponse(cinema.getId(), cinema.getName(), cinema.getRegion(), cinema.getAddress());
    }
}
