package com.ceos24.cgv.domain.store.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Getter
@Table(
        name = "purchase_item",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_purchase_item_purchase_product",
                        columnNames = {"purchase_id", "product_id"}
                )
        }
)
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    public static PurchaseItem create(
            Product product,
            Purchase purchase,
            int quantity
    ) {
        if (product == null || purchase == null) {
            throw new IllegalArgumentException("상품과 구매 정보는 필수입니다.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("구매 수량은 1 이상이어야 합니다.");
        }

        PurchaseItem purchaseItem = new PurchaseItem();
        purchaseItem.product = product;
        purchaseItem.purchase = purchase;
        purchaseItem.quantity = quantity;
        purchaseItem.unitPrice = product.getPrice();
        return purchaseItem;
    }
}
