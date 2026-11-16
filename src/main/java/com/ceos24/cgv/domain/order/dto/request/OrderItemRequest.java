package com.ceos24.cgv.domain.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "주문 항목 요청 DTO")
public record OrderItemRequest(
        @NotNull(message = "메뉴 ID는 필수입니다.") @Schema(description = "메뉴 ID", example = "1") Long menuId,
        @NotNull(message = "수량은 필수입니다.")
                @Min(value = 1, message = "주문 수량은 1개 이상이어야 합니다.")
                @Schema(description = "수량", example = "2")
                Long quantity) {}
