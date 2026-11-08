package com.ceos24.springboot.shop.dto;

import com.ceos24.springboot.shop.domain.OrderItem;

public record OrderItemResponse(
        Long menuId,
        String menuName,
        Integer menuPrice,
        Integer quantity,
        Integer amount
) {

    public static OrderItemResponse from(
            OrderItem orderItem
    ) {
        return new OrderItemResponse(
                orderItem.getMenu().getMenuId(),
                orderItem.getMenu().getMenuName(),
                orderItem.getUnitPrice(),
                orderItem.getQuantity(),
                orderItem.getUnitPrice()
                        * orderItem.getQuantity()
        );
    }
}
