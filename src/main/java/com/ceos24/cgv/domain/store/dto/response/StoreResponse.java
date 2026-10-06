package com.ceos24.cgv.domain.store.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "매장 응답 DTO")
public record StoreResponse(
        @Schema(description = "매장 ID", example = "1") Long storeId,
        @Schema(description = "매장 이름", example = "CGV 매점") String storeName,
        @Schema(description = "메뉴 재고 목록") List<MenuStockInfo> menuStocks) {}
