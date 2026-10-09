package com.ceos24.cgv.domain.store.entity;

import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 환불 요청 하나가 어느 상품을 몇 개 돌려주는지. 한 상품 라인이 여러 번 나눠 환불될 수 있어
// 좌석처럼 라인에 취소 요청 하나를 거는 것으로는 표현할 수 없다.
// 결과를 모르는 환불을 다시 보낼 때도 어느 수량의 환불이었는지 여기서 찾는다.
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseProductCancel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_product_cancel_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_cancel_id", nullable = false)
    private PaymentCancel paymentCancel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_product_id", nullable = false)
    private PurchaseProduct purchaseProduct;

    @Column(nullable = false)
    private int quantity;

    @Builder
    private PurchaseProductCancel(PaymentCancel paymentCancel, PurchaseProduct purchaseProduct, int quantity) {
        this.paymentCancel = paymentCancel;
        this.purchaseProduct = purchaseProduct;
        this.quantity = quantity;
    }
}
