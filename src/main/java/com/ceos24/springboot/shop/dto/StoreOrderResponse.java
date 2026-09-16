package com.ceos24.springboot.shop.dto;

import com.ceos24.springboot.shop.domain.OrderItem;
import com.ceos24.springboot.shop.domain.StoreOrder;

import java.time.LocalDateTime;
import java.util.List;

public record StoreOrderResponse(
        Long orderId,
        Long theaterId,
        Integer totalAmount,
        LocalDateTime orderAt,
        List<OrderItemResponse> items
) {

    public static StoreOrderResponse from(
            StoreOrder order,
            List<OrderItem> items
    ) {
        return new StoreOrderResponse(
                order.getOrderId(),
                order.getTheater().getTheaterId(),
                order.getTotalAmount(),
                order.getOrderAt(),
                items.stream()
                        .map(OrderItemResponse::from)
                        .toList()
        );
    }
}