package com.ceos24.cgv.domain.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "메뉴 생성 요청 DTO")
public record CreateMenuRequest(
        @NotBlank(message = "메뉴 이름은 필수입니다.") @Schema(description = "메뉴 이름", example = "팝콘") String name,
        @NotNull(message = "메뉴 가격은 필수입니다.")
                @Min(value = 0, message = "메뉴 가격은 0원 이상이어야 합니다.")
                @Schema(description = "메뉴 가격", example = "5000")
                Long price) {}
