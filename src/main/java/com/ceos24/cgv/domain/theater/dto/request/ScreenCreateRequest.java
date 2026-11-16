package com.ceos24.cgv.domain.theater.dto.request;

import com.ceos24.cgv.domain.theater.entity.ScreenType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "상영관 생성 요청 DTO")
public record ScreenCreateRequest(
        @NotNull(message = "상영관 타입은 필수입니다.") @Schema(description = "상영관 타입", example = "NORMAL") ScreenType screenType,
        @NotBlank(message = "상영관 이름은 필수입니다.") @Schema(description = "상영관 이름", example = "1관") String name,
        @NotNull(message = "총 좌석 수는 필수입니다.")
                @Min(value = 1, message = "총 좌석 수는 1개 이상이어야 합니다.")
                @Schema(description = "총 좌석 수", example = "100")
                Long totalSeats) {}
