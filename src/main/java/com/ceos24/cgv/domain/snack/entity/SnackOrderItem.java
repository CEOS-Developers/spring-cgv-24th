package com.ceos24.cgv.domain.snack.entity;

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
        name = "snack_order_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_snack_order_item",
                        columnNames = {
                                "snack_order_id",
                                "snack_item_id"
                        }
                )
        }
)
public class SnackOrderItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snack_order_item_id")
    private Long id;

    /**
     * 구매 수량
     */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * 주문 당시 상품 1개의 가격
     */
    @Column(name = "purchase_price", nullable = false)
    private Integer purchasePrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snack_order_id", nullable = false)
    private SnackOrder snackOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snack_item_id", nullable = false)
    private SnackItem snackItem;

    @Builder
    private SnackOrderItem(
            Integer quantity,
            Integer purchasePrice,
            SnackItem snackItem
    ) {
        validateQuantity(quantity);

        this.quantity = quantity;
        this.purchasePrice = purchasePrice;
        this.snackItem = snackItem;
    }

    public static SnackOrderItem create(
            SnackItem snackItem,
            Integer quantity
    ) {
        return SnackOrderItem.builder()
                .snackItem(snackItem)
                .quantity(quantity)
                .purchasePrice(snackItem.getPrice())
                .build();
    }

    public Integer calculateSubtotal() {
        return purchasePrice * quantity;
    }

    void assignSnackOrder(
            SnackOrder snackOrder
    ) {
        this.snackOrder = snackOrder;
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "구매 수량은 1 이상이어야 합니다."
            );
        }
    }
}