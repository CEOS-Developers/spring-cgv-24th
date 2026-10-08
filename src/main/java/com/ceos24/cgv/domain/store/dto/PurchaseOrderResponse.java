package com.ceos24.cgv.domain.store.dto;

import com.ceos24.cgv.domain.payment.dto.PaymentOrderResponse;

// 주문 내용과 결제위젯에 넘길 값을 함께 준다. 아직 결제 전이라 purchase.purchasedAt은 비어 있다.
public record PurchaseOrderResponse(
        PurchaseResponse purchase,
        PaymentOrderResponse payment
) {
}
