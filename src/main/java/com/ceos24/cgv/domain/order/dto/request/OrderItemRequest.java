package com.ceos24.cgv.domain.order.dto.request;

public record OrderItemRequest(
        Long menuId,
        Long quantity
) {}
