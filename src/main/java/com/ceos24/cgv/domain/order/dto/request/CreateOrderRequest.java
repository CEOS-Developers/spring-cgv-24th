package com.ceos24.cgv.domain.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "주문 생성 요청 DTO")
public record CreateOrderRequest(
        @NotNull(message = "매장 ID는 필수입니다.") @Schema(description = "매장 ID", example = "1") Long storeId,
        @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다.") @Valid @Schema(description = "주문 항목 목록")
                List<OrderItemRequest> items) {}
