package com.ceos24.cgv.domain.snack.dto.response;

import com.ceos24.cgv.domain.snack.entity.SnackOrder;
import com.ceos24.cgv.domain.snack.enums.SnackOrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record SnackOrderResponse(
        Long snackOrderId,
        Long userId,
        Long theaterId,
        String theaterName,
        Integer totalPrice,
        SnackOrderStatus orderStatus,
        LocalDateTime orderedAt,
        List<SnackOrderItemResponse> items
) {

    public static SnackOrderResponse from(
            SnackOrder snackOrder
    ) {
        List<SnackOrderItemResponse> itemResponses =
                snackOrder.getOrderItems()
                        .stream()
                        .map(SnackOrderItemResponse::from)
                        .toList();

        return new SnackOrderResponse(
                snackOrder.getId(),
                snackOrder.getUser().getId(),
                snackOrder.getTheater().getId(),
                snackOrder.getTheater().getName(),
                snackOrder.getTotalPrice(),
                snackOrder.getOrderStatus(),
                snackOrder.getOrderedAt(),
                itemResponses
        );
    }
}