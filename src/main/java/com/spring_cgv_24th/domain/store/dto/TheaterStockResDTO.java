package com.spring_cgv_24th.domain.store.dto;

import com.spring_cgv_24th.domain.store.entity.Product;

public record TheaterStockResDTO(
        Long productId,
        String name,
        int price,
        String description,
        String imageUrl,
        int quantity
) {

    public static TheaterStockResDTO from(Product product, int quantity) {
        return new TheaterStockResDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getDescription(),
                product.getImageUrl(),
                quantity);
    }
}
