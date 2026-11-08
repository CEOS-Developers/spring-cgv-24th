package com.ceos24.cgv.domain.theater.dto.request;

import com.ceos24.cgv.domain.theater.entity.ScreenType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상영관 생성 요청 DTO")
public record ScreenCreateRequest(
        @Schema(description = "상영관 타입", example = "NORMAL") ScreenType screenType,
        @Schema(description = "상영관 이름", example = "1관") String name,
        @Schema(description = "총 좌석 수", example = "100") Long totalSeats) {}
