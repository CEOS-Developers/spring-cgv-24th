package com.ceos.cgv.domain.concession.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InventoryCreateRequest(
        @NotNull Long cinemaId,
        @NotNull Long productId,
        @NotNull @Positive Integer stockQuantity
) {
}
