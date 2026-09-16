package com.ceos24.cgv.domain.order.dto.request;

import java.util.List;

public record CreateOrderRequest(
        Long storeId,
        List<OrderItemRequest> items
) {}
