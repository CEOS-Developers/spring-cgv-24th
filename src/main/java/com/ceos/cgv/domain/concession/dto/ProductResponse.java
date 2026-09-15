package com.ceos.cgv.domain.concession.dto;

import com.ceos.cgv.domain.concession.entity.Product;

public record ProductResponse(Long productId, String name, Long price, String description) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(), product.getName(), product.getPrice(), product.getDescription()
        );
    }
}
