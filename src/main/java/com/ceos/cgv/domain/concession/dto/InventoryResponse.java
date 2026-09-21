package com.ceos.cgv.domain.concession.dto;

import com.ceos.cgv.domain.concession.entity.Inventory;

public record InventoryResponse(
        Long inventoryId,
        Long cinemaId,
        Long productId,
        Integer stockQuantity
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getCinema().getId(),
                inventory.getProduct().getId(),
                inventory.getStockQuantity()
        );
    }
}
