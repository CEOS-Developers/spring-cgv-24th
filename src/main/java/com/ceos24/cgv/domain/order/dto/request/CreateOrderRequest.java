package com.ceos24.cgv.domain.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "주문 생성 요청 DTO")
public record CreateOrderRequest(
        @Schema(description = "매장 ID", example = "1") Long storeId,
        @Schema(description = "주문 항목 목록") List<OrderItemRequest> items) {}
