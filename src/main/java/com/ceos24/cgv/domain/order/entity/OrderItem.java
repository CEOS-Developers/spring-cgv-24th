package com.ceos24.cgv.domain.order.entity;

import com.ceos24.cgv.domain.store.entity.Menu;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
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

    private Long canceledQuantity = 0L;

    private Long price;

    protected OrderItem(Order order, Menu menu, Long quantity, Long price) {
        this.order = order;
        this.menu = menu;
        this.quantity = quantity;
        this.canceledQuantity = 0L;
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

    public Long getCancelableQuantity() {
        return this.quantity - (this.canceledQuantity != null ? this.canceledQuantity : 0L);
    }

    public Long cancelQuantity(Long cancelQty) {
        if (cancelQty == null || cancelQty <= 0) {
            throw new IllegalArgumentException("취소 수량은 1개 이상이어야 합니다.");
        }
        if (cancelQty > getCancelableQuantity()) {
            throw new BusinessException(
                    ErrorCode.ORDER_ITEM_CANCEL_QUANTITY_EXCEEDED,
                    "취소 요청 수량(" + cancelQty + "개)이 남은 취소 가능 수량(" + getCancelableQuantity() + "개)을 초과했습니다.");
        }
        this.canceledQuantity = (this.canceledQuantity != null ? this.canceledQuantity : 0L) + cancelQty;
        return cancelQty * this.price;
    }
}
