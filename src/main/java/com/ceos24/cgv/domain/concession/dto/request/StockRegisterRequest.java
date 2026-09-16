package com.ceos24.cgv.domain.concession.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockRegisterRequest(
        @NotNull(message = "상품 id는 필수입니다.")
        Long itemId,

        @NotNull(message = "재고 수량은 필수입니다.")
        @Min(value = 1, message = "재고는 1 이상으로 등록해야 합니다.")
        Integer quantity
) {
}
