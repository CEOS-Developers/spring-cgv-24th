package com.ceos24.cgv.domain.order.entity;

import com.ceos24.cgv.domain.store.entity.Menu;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;

    @JoinColumn(nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Menu menu;

    private Long quantity;

    private Long price;

    protected OrderItem(Order order, Menu menu, Long quantity, Long price) {
        this.order = order;
        this.menu = menu;
        this.quantity = quantity;
        this.price = price;
    }

    public static OrderItem create(Menu menu, Long quantity) {
        if (menu == null) {
            throw new IllegalArgumentException("메뉴 정보는 필수입니다.");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1개 이상이어야 합니다.");
        }
        return new OrderItem(null, menu, quantity, menu.getPrice());
    }

    public void assignOrder(Order order) {
        this.order = order;
    }

    public Long getSubtotal() {
        return this.price * this.quantity;
    }
}
