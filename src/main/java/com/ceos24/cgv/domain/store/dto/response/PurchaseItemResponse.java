package com.ceos24.cgv.domain.store.dto.response;

import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.PurchaseItem;

public record PurchaseItemResponse(
        Long productId,
        String productName,
        int quantity,
        int unitPrice,
        long totalPrice
) {

    public static PurchaseItemResponse from(
            PurchaseItem purchaseItem
    ) {
        Product product = purchaseItem.getProduct();

        return new PurchaseItemResponse(
                product.getId(),
                product.getName(),
                purchaseItem.getQuantity(),
                purchaseItem.getUnitPrice(),
                (long) purchaseItem.getUnitPrice() * purchaseItem.getQuantity()
        );
    }
}
