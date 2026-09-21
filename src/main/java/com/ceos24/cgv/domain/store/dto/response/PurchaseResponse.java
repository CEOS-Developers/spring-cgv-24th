package com.ceos24.cgv.domain.store.dto.response;

import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseItem;

import java.time.LocalDateTime;
import java.util.List;

public record PurchaseResponse(
        Long id,
        Long userId,
        Long cinemaId,
        String cinemaName,
        LocalDateTime purchasedAt,
        long totalPrice,
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

        long totalPrice = itemResponses.stream()
                .mapToLong(PurchaseItemResponse::totalPrice)
                .sum();

        return new PurchaseResponse(
                purchase.getId(),
                purchase.getUser().getId(),
                purchase.getCinema().getId(),
                purchase.getCinema().getName(),
                purchase.getPurchasedAt(),
                totalPrice,
                itemResponses
        );
    }
}
