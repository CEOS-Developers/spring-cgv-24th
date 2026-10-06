package com.ceos24.cgv.domain.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메뉴 항목 DTO")
public record MenuItem(
        @Schema(description = "메뉴 ID", example = "1") Long menuId,
        @Schema(description = "재고 수량", example = "50") Long stock) {}
