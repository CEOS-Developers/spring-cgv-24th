package com.ceos24.cgv.domain.theater.dto;

import com.ceos24.cgv.domain.theater.entity.Theater;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "영화관 정보 DTO")
public record TheaterInfo(
        @Schema(description = "영화관 ID", example = "1") Long theaterId,
        @Schema(description = "영화관 이름", example = "CGV 강남") String theaterName,
        @Schema(description = "영화관 주소", example = "서울시 강남구") String address) {
    public static TheaterInfo from(Theater theater) {
        return new TheaterInfo(theater.getId(), theater.getName(), theater.getAddress());
    }
}
