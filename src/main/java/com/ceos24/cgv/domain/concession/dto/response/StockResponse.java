package com.ceos24.cgv.domain.concession.dto.response;

import com.ceos24.cgv.domain.concession.entity.Stock;

public record StockResponse(
        Long itemId,
        String itemName,
        Integer price,
        Integer quantity
) {
    public static StockResponse from(Stock stock) {
        return new StockResponse(
                stock.getItem().getId(),
                stock.getItem().getName(),
                stock.getItem().getPrice(),
                stock.getQuantity()
        );
    }
}
