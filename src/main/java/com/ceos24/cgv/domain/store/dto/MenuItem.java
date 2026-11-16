package com.ceos24.cgv.domain.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "메뉴 항목 DTO")
public record MenuItem(
        @NotNull(message = "메뉴 ID는 필수입니다.") @Schema(description = "메뉴 ID", example = "1") Long menuId,
        @NotNull(message = "재고 수량은 필수입니다.")
                @Min(value = 0, message = "재고 수량은 0개 이상이어야 합니다.")
                @Schema(description = "재고 수량", example = "50")
                Long stock) {}
