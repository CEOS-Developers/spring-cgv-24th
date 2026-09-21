package com.ceos.cgv.domain.concession.dto;

import com.ceos.cgv.domain.concession.entity.FoodOrder;

import java.time.LocalDateTime;
import java.util.List;

public record FoodOrderResponse(
        Long orderId,
        Long userId,
        Long cinemaId,
        Long totalPrice,
        LocalDateTime createdAt,
        List<FoodOrderItemResponse> items
) {
    public static FoodOrderResponse from(FoodOrder order) {
        return new FoodOrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getCinema().getId(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                order.getItems().stream().map(FoodOrderItemResponse::from).toList()
        );
    }
}
