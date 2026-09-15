package com.ceos.cgv.domain.concession.dto;

import com.ceos.cgv.domain.concession.entity.OrderItem;

public record FoodOrderItemResponse(
        Long productId,
        String productName,
        Integer quantity,
        Long unitPrice
) {
    public static FoodOrderItemResponse from(OrderItem item) {
        return new FoodOrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }
}
