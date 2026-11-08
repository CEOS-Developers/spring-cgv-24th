package com.ceos24.cgv.domain.theater.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "영화관 생성 요청 DTO")
public record CreateTheaterRequest(
        @NotBlank(message = "영화관 이름은 필수입니다.") @Schema(description = "영화관 이름", example = "CGV 강남") String name,
        @NotBlank(message = "영화관 주소는 필수입니다.") @Schema(description = "영화관 주소", example = "서울시 강남구") String address) {}
