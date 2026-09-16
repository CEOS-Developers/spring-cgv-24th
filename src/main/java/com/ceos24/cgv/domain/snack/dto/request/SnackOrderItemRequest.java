package com.ceos24.cgv.domain.snack.dto.request;

public record SnackOrderItemRequest(
        Long snackItemId,
        Integer quantity
) {
}