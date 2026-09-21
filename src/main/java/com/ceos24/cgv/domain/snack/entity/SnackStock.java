package com.ceos24.cgv.domain.snack.entity;

import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "snack_stocks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_snack_stock_theater_item",
                        columnNames = {
                                "theater_id",
                                "snack_item_id"
                        }
                )
        }
)
public class SnackStock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snack_stock_id")
    private Long id;

    /**
     * 현재 재고 수량
     */
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snack_item_id", nullable = false)
    private SnackItem snackItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @Builder
    private SnackStock(
            Integer stockQuantity,
            SnackItem snackItem,
            Theater theater
    ) {
        validateStockQuantity(stockQuantity);

        this.stockQuantity = stockQuantity;
        this.snackItem = snackItem;
        this.theater = theater;
    }

    /**
     * 상품 구매에 따른 재고 차감
     */
    public void decreaseStock(Integer quantity) {
        validatePurchaseQuantity(quantity);

        if (stockQuantity < quantity) {
            throw new IllegalArgumentException(
                    "상품 재고가 부족합니다."
            );
        }

        this.stockQuantity -= quantity;
    }

    /**
     * 상품 입고에 따른 재고 증가
     */
    public void increaseStock(Integer quantity) {
        validatePurchaseQuantity(quantity);
        this.stockQuantity += quantity;
    }

    private void validateStockQuantity(Integer stockQuantity) {
        if (stockQuantity == null || stockQuantity < 0) {
            throw new IllegalArgumentException(
                    "재고 수량은 0 이상이어야 합니다."
            );
        }
    }

    private void validatePurchaseQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "상품 수량은 1 이상이어야 합니다."
            );
        }
    }
}