package com.cgvclone.cgv.domain.purchase.dto;

public record PurchaseItemRequest(
        Long goodsId,
        Integer quantity
) {
}
