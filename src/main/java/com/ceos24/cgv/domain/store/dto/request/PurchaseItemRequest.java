package com.ceos24.cgv.domain.store.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 상품별 구매 요청 DTO
 */
public record PurchaseItemRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        @Positive(message = "상품 ID는 1 이상이어야 합니다.")
        Long productId,

        @NotNull(message = "구매 수량은 필수입니다.")
        @Positive(message = "구매 수량은 1 이상이어야 합니다.")
        Integer quantity
) {
}
