package com.ceos24.cgv.domain.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// 구매자는 본문이 아니라 토큰에서 정한다.
public record PurchaseCreateRequest(
        @NotNull Long branchId,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity
    ) {
    }
}
