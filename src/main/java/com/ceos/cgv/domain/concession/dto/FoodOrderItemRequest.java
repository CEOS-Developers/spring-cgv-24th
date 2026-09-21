package com.ceos.cgv.domain.concession.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FoodOrderItemRequest(
        @NotNull Long productId,
        @NotNull @Positive Integer quantity
) {
}
