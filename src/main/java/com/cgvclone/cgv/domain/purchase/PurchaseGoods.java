package com.cgvclone.cgv.domain.purchase;

import com.cgvclone.cgv.domain.goods.Goods;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "purchase_goods", uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchase_goods", columnNames = {"purchase_id", "goods_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseGoods {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long purchaseGoodsId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id", nullable = false, updatable = false)
    private Goods goods;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    // 상품 정보가 변경되어도 구매 당시의 상품명과 단가를 유지한다.
    @Column(nullable = false, length = 100, updatable = false)
    private String goodsName;

    @Column(nullable = false, updatable = false)
    private Integer unitPrice;

    @Column(nullable = false, updatable = false)
    private Integer quantity;

    PurchaseGoods(Purchase purchase, Goods goods, Integer quantity) {
        this.purchase = Objects.requireNonNull(purchase, "구매 내역은 필수입니다.");
        this.goods = Objects.requireNonNull(goods, "상품은 필수입니다.");
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("구매 수량은 1 이상이어야 합니다.");
        }
        this.goodsName = goods.getName();
        this.unitPrice = goods.getPrice();
        this.quantity = quantity;
    }

    public int getTotalPrice() {
        return Math.multiplyExact(unitPrice, quantity);
    }
}
