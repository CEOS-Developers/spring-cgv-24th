package com.ceos24.cgv.domain.concession.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull(message = "상품 id는 필수입니다.")
        Long itemId,

        @NotNull(message = "수량은 필수입니다.")
        @Positive(message = "수량은 양수여야 합니다.")
        Integer quantity
) {
}
