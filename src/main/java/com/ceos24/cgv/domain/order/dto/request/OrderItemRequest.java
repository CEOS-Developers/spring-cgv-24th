package com.ceos24.cgv.domain.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주문 항목 요청 DTO")
public record OrderItemRequest(
        @Schema(description = "메뉴 ID", example = "1") Long menuId,
        @Schema(description = "수량", example = "2") Long quantity) {}
