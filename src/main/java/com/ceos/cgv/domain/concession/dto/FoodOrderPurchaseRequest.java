package com.ceos.cgv.domain.concession.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FoodOrderPurchaseRequest(
        @NotNull Long cinemaId,
        @NotEmpty List<@Valid FoodOrderItemRequest> items
) {
    public FoodOrderCreateRequest forUser(Long userId) {
        return new FoodOrderCreateRequest(userId, cinemaId, items);
    }
}
