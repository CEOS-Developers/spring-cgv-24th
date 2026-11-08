package com.ceos24.cgv.domain.store.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메뉴 재고 정보 DTO")
public record MenuStockInfo(
        @Schema(description = "메뉴 ID", example = "1") Long menuId,
        @Schema(description = "메뉴 이름", example = "팝콘") String name,
        @Schema(description = "메뉴 가격", example = "5000") Long price,
        @Schema(description = "재고 수량", example = "50") Long stock) {}
