package com.ceos24.cgv.domain.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// 환불할 상품과 수량만 받는다. 금액은 받지 않는다. 서버가 구매 시점 단가(unitPrice)로 계산한다.
public record PurchaseRefundRequest(
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity
    ) {
    }
}
