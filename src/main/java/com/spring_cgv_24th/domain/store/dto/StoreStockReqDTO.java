package com.spring_cgv_24th.domain.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StoreStockReqDTO(
        @Schema(description = "현재 재고에 추가할 수량")
        @NotNull @Positive Integer quantity
) {
}
