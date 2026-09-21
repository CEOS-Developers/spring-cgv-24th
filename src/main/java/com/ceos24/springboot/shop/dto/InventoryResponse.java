package com.ceos24.springboot.shop.dto;

import com.ceos24.springboot.shop.domain.StoreInventory;

public record InventoryResponse(
        Long inventoryId,
        Long theaterId,
        Long menuId,
        String menuName,
        Integer menuPrice,
        Integer stock
) {

    public static InventoryResponse from(
            StoreInventory inventory
    ) {
        return new InventoryResponse(
                inventory.getInventoryId(),
                inventory.getTheater().getTheaterId(),
                inventory.getMenu().getMenuId(),
                inventory.getMenu().getMenuName(),
                inventory.getMenu().getMenuPrice(),
                inventory.getStock()
        );
    }
}