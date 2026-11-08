package com.ceos24.cgv.domain.store.dto;

import com.ceos24.cgv.domain.store.entity.PurchaseStatus;

import java.time.LocalDateTime;

// 구매 내역 조회의 한 행 = 구매 항목 하나. 헤더 값은 항목 수만큼 반복된다.
public record PurchaseHistoryRow(
        Long purchaseId,
        Long branchId,
        String branchName,
        PurchaseStatus status,
        int totalPrice,
        LocalDateTime purchasedAt,
        LocalDateTime cancelledAt,
        Long productId,
        String productName,
        int quantity,
        int canceledQuantity,
        int unitPrice
) {
}
