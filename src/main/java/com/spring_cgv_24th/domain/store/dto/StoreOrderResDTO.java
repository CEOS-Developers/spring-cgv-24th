package com.spring_cgv_24th.domain.store.dto;

import com.spring_cgv_24th.domain.store.entity.StoreOrder;
import com.spring_cgv_24th.domain.store.entity.StoreOrderItem;
import com.spring_cgv_24th.domain.store.enums.StoreOrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public record StoreOrderResDTO(
        Long orderId,
        Long memberId,
        Long theaterId,
        LocalDateTime purchasedAt,
        List<OrderItemDTO> items,
        long totalPrice,
        StoreOrderStatus status,
        LocalDateTime expiresAt,
        String paymentOrderId
) {

    public static StoreOrderResDTO from(StoreOrder order, List<StoreOrderItem> items) {
        return new StoreOrderResDTO(
                order.getId(),
                order.getMember().getId(),
                order.getTheater().getId(),
                order.getPurchasedAt(),
                items.stream().map(OrderItemDTO::from).toList(),
                items.stream().mapToLong(StoreOrderItem::getTotalPrice).sum(),
                order.getStatus(),
                order.getExpiresAt(),
                order.getPaymentOrderId());
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
