package com.ceos.cgv.domain.concession.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FoodOrderCreateRequest(
        @NotNull Long userId,
        @NotNull Long cinemaId,
        @NotEmpty List<@Valid FoodOrderItemRequest> items
) {
}
