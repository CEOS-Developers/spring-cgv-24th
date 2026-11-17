package com.ceos.cgv.domain.concession.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ceos.cgv.domain.concession.entity.FoodOrder;
import com.ceos.cgv.domain.concession.enums.FoodOrderPaymentStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record FoodOrderResponse(
        Long orderId,
        Long userId,
        Long cinemaId,
        Long totalPrice,
        LocalDateTime createdAt,
        List<FoodOrderItemResponse> items,
        @JsonInclude(JsonInclude.Include.NON_NULL) FoodOrderPaymentStatus paymentStatus,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant holdExpiresAt
) {
    public static FoodOrderResponse from(FoodOrder order) {
        return new FoodOrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getCinema().getId(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                order.getItems().stream().map(FoodOrderItemResponse::from).toList(),
                order.getPaymentStatus(),
                order.getHoldExpiresAt()
        );
    }
}
