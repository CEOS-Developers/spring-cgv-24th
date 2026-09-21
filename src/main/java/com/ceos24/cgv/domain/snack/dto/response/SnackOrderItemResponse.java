package com.ceos24.cgv.domain.snack.dto.response;

import com.ceos24.cgv.domain.snack.entity.SnackOrderItem;

public record SnackOrderItemResponse(
        Long snackOrderItemId,
        Long snackItemId,
        String snackItemName,
        Integer quantity,
        Integer purchasePrice,
        Integer subtotal
) {

    public static SnackOrderItemResponse from(
            SnackOrderItem orderItem
    ) {
        return new SnackOrderItemResponse(
                orderItem.getId(),
                orderItem.getSnackItem().getId(),
                orderItem.getSnackItem().getName(),
                orderItem.getQuantity(),
                orderItem.getPurchasePrice(),
                orderItem.calculateSubtotal()
        );
    }
}