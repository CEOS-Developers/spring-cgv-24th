package com.ceos24.cgv.domain.concession.dto.response;

import com.ceos24.cgv.domain.concession.entity.OrderItem;

public record OrderItemResponse(
        Long itemId,
        String itemName,
        Integer quantity,
        Integer price
) {
    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getItem().getId(),
                orderItem.getItem().getName(),
                orderItem.getQuantity(),
                orderItem.getPrice()
        );
    }
}
