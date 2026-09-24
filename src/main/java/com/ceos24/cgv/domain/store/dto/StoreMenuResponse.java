package com.ceos24.cgv.domain.store.dto;

import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.Stock;

// 원재고는 내리지 않는다. 최소 재고 1개는 팔 수 없으므로 고객에게 의미 있는 건 판매 가능 수량이다.
public record StoreMenuResponse(
        Long productId,
        String name,
        int price,
        int availableQuantity,
        boolean soldOut
) {
    public static StoreMenuResponse from(Stock stock) {
        Product product = stock.getProduct();
        return new StoreMenuResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                stock.availableQuantity(),
                stock.isSoldOut()
        );
    }
}
