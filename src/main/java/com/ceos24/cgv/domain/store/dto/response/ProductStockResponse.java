package com.ceos24.cgv.domain.store.dto.response;

import com.ceos24.cgv.domain.store.entity.CinemaStock;
import com.ceos24.cgv.domain.store.entity.Product;

public record ProductStockResponse(
        Long productId,
        String name,
        Integer price,
        String description,
        Integer quantity
) {

    public static ProductStockResponse from(
            CinemaStock cinemaStock
    ) {
        Product product = cinemaStock.getProduct();
        return new ProductStockResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getDescription(),
                cinemaStock.getQuantity()
        );
    }
}
