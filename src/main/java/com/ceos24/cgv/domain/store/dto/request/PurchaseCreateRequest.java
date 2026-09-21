package com.ceos24.cgv.domain.store.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 전체 구매 요청 DTO
 */
public record PurchaseCreateRequest(
        @NotNull(message = "영화관 ID는 필수입니다.")
        @Positive(message = "영화관 ID는 1 이상이어야 합니다.")
        Long cinemaId,

        @NotEmpty(message = "한 개 이상의 상품을 선택해야 합니다.")
        List<@Valid PurchaseItemRequest> items
) {
}
