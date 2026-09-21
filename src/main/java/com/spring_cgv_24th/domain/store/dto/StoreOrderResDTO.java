package com.spring_cgv_24th.domain.store.dto;

import com.spring_cgv_24th.domain.store.entity.StoreOrder;
import com.spring_cgv_24th.domain.store.entity.StoreOrderItem;
import java.time.LocalDateTime;
import java.util.List;

public record StoreOrderResDTO(
        Long orderId,
        Long memberId,
        Long theaterId,
        LocalDateTime purchasedAt,
        List<OrderItemDTO> items,
        long totalPrice
) {

    public static StoreOrderResDTO from(StoreOrder order, List<StoreOrderItem> items) {
        return new StoreOrderResDTO(
                order.getId(),
                order.getMember().getId(),
                order.getTheater().getId(),
                order.getPurchasedAt(),
                items.stream().map(OrderItemDTO::from).toList(),
                items.stream().mapToLong(item -> (long) item.getUnitPrice() * item.getQuantity()).sum());
    }

    public record OrderItemDTO(
            Long productId,
            String name,
            int quantity,
            int unitPrice
    ) {

        public static OrderItemDTO from(StoreOrderItem item) {
            return new OrderItemDTO(
                    item.getProduct().getId(),
                    item.getProduct().getName(),
                    item.getQuantity(),
                    item.getUnitPrice());
        }
    }
}
