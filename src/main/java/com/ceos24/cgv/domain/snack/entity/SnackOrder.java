package com.ceos24.cgv.domain.snack.entity;

import com.ceos24.cgv.domain.snack.enums.SnackOrderStatus;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "snack_orders")
public class SnackOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snack_order_id")
    private Long id;

    /**
     * 주문 전체 결제 금액
     */
    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private SnackOrderStatus orderStatus;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @OneToMany(
            mappedBy = "snackOrder",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private final List<SnackOrderItem> orderItems =
            new ArrayList<>();

    private SnackOrder(
            User user,
            Theater theater
    ) {
        this.user = user;
        this.theater = theater;
        this.totalPrice = 0;
        this.orderStatus = SnackOrderStatus.PENDING;
        this.orderedAt = LocalDateTime.now();
    }

    public static SnackOrder create(
            User user,
            Theater theater
    ) {
        return new SnackOrder(user, theater);
    }

    /**
     * 주문에 상품 추가
     */
    public void addOrderItem(
            SnackOrderItem orderItem
    ) {
        orderItems.add(orderItem);
        orderItem.assignSnackOrder(this);

        this.totalPrice += orderItem.calculateSubtotal();
    }

    /**
     * 결제 완료 처리
     */
    public void complete() {
        this.orderStatus = SnackOrderStatus.COMPLETED;
    }
}