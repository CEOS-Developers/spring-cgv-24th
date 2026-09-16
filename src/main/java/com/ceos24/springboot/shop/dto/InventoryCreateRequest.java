package com.ceos24.springboot.shop.dto;

public record InventoryCreateRequest(
        Long menuId,
        Integer stock
) {
}