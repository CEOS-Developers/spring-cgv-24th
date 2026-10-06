package com.ceos24.cgv.domain.theater.dto;

import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.ScreenType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상영관 정보 DTO")
public record ScreenInfo(
        @Schema(description = "상영관 ID", example = "1") Long screenId,
        @Schema(description = "상영관 타입", example = "NORMAL") ScreenType screenType,
        @Schema(description = "상영관 이름", example = "1관") String name,
        @Schema(description = "총 좌석 수", example = "100") Long totalSeats) {
    public static ScreenInfo from(Screen screen) {
        return new ScreenInfo(screen.getId(), screen.getScreenType(), screen.getName(), screen.getTotalSeats());
    }
}
