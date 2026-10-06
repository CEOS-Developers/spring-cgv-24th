package com.ceos24.springboot.shop.domain;

import com.ceos24.springboot.theater.domain.Theater;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Getter
@Table(name = "store_inventory")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreInventory {
//  영화관 당 매점의 재고 수량을 나타냄

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Long InventoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id")
    private Theater theater;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id")
    private Menu menu;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Builder
    public StoreInventory(
            Theater theater,
            Menu menu,
            Integer stock
    ) {
        this.theater = theater;
        this.menu = menu;
        this.stock = stock;
    }

//    재고 차감 메서드
    public void decreaseStock(Integer quantity) {

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "주문 수량은 1개 이상이어야 합니다."
            );
        }

        if (stock < quantity) {
            throw new IllegalArgumentException(
                    "재고가 부족합니다."
            );
        }

        this.stock -= quantity;
    }

// 재고 추가 메서드
    public void increaseStock(Integer addQuantity) {
        if (addQuantity == null || addQuantity <= 0) {
            throw new IllegalArgumentException(
                    "추가할 재고 수량은 1개 이상이어야 합니다."
            );
        }

        this.stock += addQuantity;
    }

}