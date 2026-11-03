package com.ceos24.cgv.domain.store.dto.response;

import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;

import java.time.LocalDateTime;

public record PurchaseCreateResponse(
        Long purchaseId,
        String orderId,
        long totalAmount,
        PurchaseStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
    public static PurchaseCreateResponse from(Purchase purchase) {
        return new PurchaseCreateResponse(
                purchase.getId(),
                purchase.getOrderId(),
                purchase.getTotalAmount(),
                purchase.getStatus(),
                purchase.getCreatedAt(),
                purchase.getExpiresAt()
        );
    }
}
