package com.ceos24.springboot.shop.dto;

public record OrderItemRequest(
        Long menuId,
        Integer quantity
) {
}