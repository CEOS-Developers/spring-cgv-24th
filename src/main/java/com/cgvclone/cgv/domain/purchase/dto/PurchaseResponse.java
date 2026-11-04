package com.cgvclone.cgv.domain.purchase.dto;

import com.cgvclone.cgv.domain.purchase.Purchase;
import com.cgvclone.cgv.domain.purchase.PurchaseStatus;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseResponse(
        Long purchaseId,
        Long cinemaId,
        LocalDateTime purchasedAt,
        Integer totalPrice,
        List<Item> items,
        String orderId,
        PurchaseStatus status,
        LocalDateTime approvedAt
) {

    public static PurchaseResponse from(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getPurchaseId(),
                purchase.getCinema().getCinemaId(),
                purchase.getPurchasedAt(),
                purchase.getTotalPrice(),
                purchase.getPurchaseGoods().stream()
                        .map(item -> new Item(item.getGoods().getGoodsId(), item.getGoodsName(), item.getUnitPrice(),
                                item.getQuantity(), item.getTotalPrice()))
                        .toList(),
                purchase.getOrderId(),
                purchase.getStatus(),
                purchase.getApprovedAt()
        );
    }

    public record Item(
            Long goodsId,
            String goodsName,
            Integer unitPrice,
            Integer quantity,
            Integer totalPrice
    ) {
    }
}
