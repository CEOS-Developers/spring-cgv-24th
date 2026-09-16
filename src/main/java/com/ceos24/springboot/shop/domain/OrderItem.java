package com.ceos24.springboot.shop.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {
//  구매 내역 목록을 메뉴당 표현함.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long OrderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private StoreOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id")
    private Menu menu;

    @Column(name = "quantity")
    private Integer quantity;

    @Builder
    public OrderItem(
            StoreOrder order,
            Menu menu,
            Integer quantity
    ) {
        this.order = order;
        this.menu = menu;
        this.quantity = quantity;
    }
}