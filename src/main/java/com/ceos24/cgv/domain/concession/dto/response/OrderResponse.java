package com.ceos24.cgv.domain.concession.dto.response;

import com.ceos24.cgv.domain.concession.entity.Order;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long orderId,
        String theaterName,
        List<OrderItemResponse> items,
        Integer totalPrice,
        Instant orderedAt
) {
    public static OrderResponse of(Order order, List<OrderItemResponse> items) {
        return new OrderResponse(
                order.getId(),
                order.getTheater().getName(),
                items,
                order.getTotalPrice(),
                order.getOrderedAt()
        );
    }
}
