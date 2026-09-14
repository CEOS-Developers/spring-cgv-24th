package com.ceos24.cgv.domain.concession.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer price;

    private OrderItem(Order order, Item item, Integer quantity, Integer price) {
        this.order = order;
        this.item = item;
        this.quantity = quantity;
        this.price = price;
    }

    public static OrderItem create(Order order, Item item, Integer quantity, Integer price) {
        return new OrderItem(order, item, quantity, price);
    }
}
