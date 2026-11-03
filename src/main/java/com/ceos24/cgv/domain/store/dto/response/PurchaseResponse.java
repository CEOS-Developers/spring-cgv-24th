package com.ceos24.cgv.domain.store.dto.response;

import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseItem;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PurchaseResponse(
        Long id,
        String orderId,
        Long userId,
        Long cinemaId,
        String cinemaName,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        PurchaseStatus status,
        long totalAmount,
        List<PurchaseItemResponse> items
) {

    public static PurchaseResponse from(
            Purchase purchase,
            List<PurchaseItem> purchaseItems
    ) {
        List<PurchaseItemResponse> itemResponses =
                purchaseItems.stream()
                        .map(PurchaseItemResponse::from)
                        .toList();

        return new PurchaseResponse(
                purchase.getId(),
                purchase.getOrderId(),
                purchase.getUserEntity().getId(),
                purchase.getCinema().getId(),
                purchase.getCinema().getName(),
                purchase.getCreatedAt(),
                purchase.getExpiresAt(),
                purchase.getStatus(),
                purchase.getTotalAmount(),
                itemResponses
        );
    }
}
