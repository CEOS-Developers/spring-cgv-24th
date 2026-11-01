package com.cgvclone.cgv.domain.purchase.dto;

import java.util.List;

public record PurchaseCreateRequest(
        Long cinemaId,
        List<PurchaseItemRequest> items
) {
}
