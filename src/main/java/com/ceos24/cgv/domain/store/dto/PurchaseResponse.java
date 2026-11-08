package com.ceos24.cgv.domain.store.dto;

import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseProduct;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public record PurchaseResponse(
        Long purchaseId,
        Long branchId,
        String branchName,
        PurchaseStatus status,
        String statusName,
        // 처음 산 금액이다. 환불해도 줄지 않아, 무엇을 얼마에 샀었는지가 남는다.
        int totalPrice,
        // 환불되지 않은 수량의 금액이다.
        int remainingPrice,
        LocalDateTime purchasedAt,
        LocalDateTime cancelledAt,
        List<Item> items
) {
    public record Item(
            Long productId,
            String name,
            int quantity,
            int canceledQuantity,
            int unitPrice,
            int subtotal
    ) {
        private static Item from(PurchaseProduct item) {
            return new Item(
                    item.getProduct().getId(),
                    item.getProduct().getName(),
                    item.getQuantity(),
                    item.getCanceledQuantity(),
                    item.getUnitPrice(),
                    item.getUnitPrice() * item.getQuantity()
            );
        }

        private static Item from(PurchaseHistoryRow row) {
            return new Item(
                    row.productId(),
                    row.productName(),
                    row.quantity(),
                    row.canceledQuantity(),
                    row.unitPrice(),
                    row.unitPrice() * row.quantity()
            );
        }

        private int remainingSubtotal() {
            return unitPrice * (quantity - canceledQuantity);
        }
    }

    public static PurchaseResponse from(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getBranch().getId(),
                purchase.getBranch().getName(),
                purchase.getStatus(),
                purchase.getStatus().getDisplayName(),
                purchase.getTotalPrice(),
                purchase.getRemainingPrice(),
                purchase.getPurchasedAt(),
                purchase.getCancelledAt(),
                purchase.getItems().stream().map(Item::from).toList()
        );
    }

    // 행은 구매 id 내림차순으로 온다. 삽입 순서를 지키는 맵으로 묶어 그 순서를 유지한다.
    public static List<PurchaseResponse> listOf(List<PurchaseHistoryRow> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(PurchaseHistoryRow::purchaseId, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(PurchaseResponse::of)
                .toList();
    }

    private static PurchaseResponse of(List<PurchaseHistoryRow> rows) {
        PurchaseHistoryRow header = rows.getFirst();
        List<Item> items = rows.stream().map(Item::from).toList();
        return new PurchaseResponse(
                header.purchaseId(),
                header.branchId(),
                header.branchName(),
                header.status(),
                header.status().getDisplayName(),
                header.totalPrice(),
                items.stream().mapToInt(Item::remainingSubtotal).sum(),
                header.purchasedAt(),
                header.cancelledAt(),
                items
        );
    }
}
