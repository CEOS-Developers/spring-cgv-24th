package com.ceos24.cgv.domain.store.entity;

import com.ceos24.cgv.global.entity.BaseTimeEntity;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseProduct extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int unitPrice;

    // 환불이 확정된 수량의 합이다. 처음 산 수량(quantity)은 바꾸지 않아 무엇을 몇 개 샀었는지가 남는다.
    @Column(nullable = false)
    private int canceledQuantity;

    @Builder
    private PurchaseProduct(Purchase purchase, Product product, int quantity, int unitPrice) {
        this.purchase = purchase;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public int getRemainingQuantity() {
        return this.quantity - this.canceledQuantity;
    }

    // 요청 시점에 검사해도 확정 시점에 다시 본다. 두 환불이 같은 남은 수량을 보고 함께 통과하는 일을 막는다.
    public void verifyCancelable(int cancelQuantity) {
        if (cancelQuantity < 1 || cancelQuantity > getRemainingQuantity()) {
            throw new CustomException(ErrorCode.CANCEL_QUANTITY_EXCEEDED);
        }
    }

    void applyCancel(int cancelQuantity) {
        verifyCancelable(cancelQuantity);
        this.canceledQuantity += cancelQuantity;
    }
}
