package com.ceos24.cgv.domain.order.entity;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.store.entity.Store;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;

    @JoinColumn(nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Store store;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    private List<OrderItem> orderItems = new ArrayList<>();

    private Long totalPrice;

    private LocalDateTime createdAt;

    protected Order(Member member, Store store) {
        this.member = member;
        this.store = store;
        this.createdAt = LocalDateTime.now();
    }

    public static Order create(Member member, Store store, List<OrderItem> orderItems) {
        validateOrder(member, store, orderItems);

        Order order = new Order(member, store);
        for (OrderItem item : orderItems) {
            order.addOrderItem(item);
        }
        order.totalPrice = order.calculateTotalPrice();
        return order;
    }

    private static void validateOrder(Member member, Store store, List<OrderItem> orderItems) {
        if (member == null) {
            throw new IllegalArgumentException("주문자 정보는 필수입니다.");
        }
        if (store == null) {
            throw new IllegalArgumentException("스토어 정보는 필수입니다.");
        }
        if (orderItems == null || orderItems.isEmpty()) {
            throw new IllegalArgumentException("주문 항목은 1개 이상이어야 합니다.");
        }
    }

    private void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
        orderItem.assignOrder(this);
    }

    public Long calculateTotalPrice() {
        return orderItems.stream().mapToLong(OrderItem::getSubtotal).sum();
    }

    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }
}
