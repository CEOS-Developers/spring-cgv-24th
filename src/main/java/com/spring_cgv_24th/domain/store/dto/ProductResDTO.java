package com.spring_cgv_24th.domain.store.dto;

import com.spring_cgv_24th.domain.store.entity.Product;

public record ProductResDTO(
        Long productId,
        String name,
        int price,
        String description,
        String imageUrl
) {

    public static ProductResDTO from(Product product) {
        return new ProductResDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getDescription(),
                product.getImageUrl());
    }
}
