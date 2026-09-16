package com.ceos24.cgv.domain.concession.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderCreateRequest(
        @NotNull(message = "영화관은 필수입니다.")
        Long theaterId,

        @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다.")
        List<@NotNull(message = "주문 상품 정보는 null일 수 없습니다.") @Valid OrderItemRequest> items
) {
}
