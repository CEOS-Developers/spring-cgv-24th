package com.ceos24.springboot.shop.dto;

import java.util.List;

public record StoreOrderCreateRequest(
        Long theaterId,
        List<OrderItemRequest> items
) {
}