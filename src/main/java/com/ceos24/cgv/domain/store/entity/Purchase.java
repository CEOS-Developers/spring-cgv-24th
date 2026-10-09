package com.ceos24.cgv.domain.store.entity;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.global.entity.BaseTimeEntity;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Purchase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private int totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status;

    // 결제가 승인된 시각. 주문을 만든 시각은 createdAt이 갖는다.
    private LocalDateTime purchasedAt;

    // 모든 수량이 환불된 시각.
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseProduct> items = new ArrayList<>();

    @Builder
    private Purchase(User user, Branch branch) {
        this.user = user;
        this.branch = branch;
        this.totalPrice = 0;
        this.status = PurchaseStatus.PENDING;
    }

    // 자식 항목 합계와 어긋나지 않도록 이 메서드 안에서만 총액을 갱신한다.
    // 단가는 지금 상품 가격을 복사한다. 호출자에게 받으면 상품 가격과 다른 값이 들어올 틈이 생긴다.
    public void addItem(Product product, int quantity) {
        int unitPrice = product.getPrice();
        PurchaseProduct item = PurchaseProduct.builder()
                .purchase(this)
                .product(product)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();
        this.items.add(item);
        this.totalPrice += unitPrice * quantity;
    }

    // 결제를 다시 하려면 새 주문을 만든다. 구매 하나에 결제 시도는 하나다.
    public void verifyPayable() {
        if (this.status != PurchaseStatus.PENDING) {
            throw new CustomException(ErrorCode.PURCHASE_NOT_PENDING);
        }
    }

    // 시각을 직접 읽지 않고 받는다. 서비스가 주입받은 Clock 기준으로 기록해야 테스트에서 시각을 고정할 수 있다.
    public void complete(LocalDateTime now) {
        verifyPayable();
        this.status = PurchaseStatus.PAID;
        this.purchasedAt = now;
    }

    public void fail() {
        verifyPayable();
        this.status = PurchaseStatus.FAILED;
    }

    // 결제가 끝난 구매만 환불한다. 모두 환불된 구매도 여기서 걸린다.
    public void verifyRefundable() {
        if (this.status != PurchaseStatus.PAID) {
            throw new CustomException(ErrorCode.PURCHASE_NOT_PAID);
        }
    }

    // 환불이 확인된 수량을 반영한다. 남은 수량이 없으면 구매 전체가 취소된 것이다.
    public void completeRefund(List<PurchaseProductCancel> details, LocalDateTime now) {
        details.forEach(detail -> detail.getPurchaseProduct().applyCancel(detail.getQuantity()));
        if (this.items.stream().allMatch(item -> item.getRemainingQuantity() == 0)) {
            this.status = PurchaseStatus.CANCELLED;
            this.cancelledAt = now;
        }
    }

    // 환불되지 않은 수량의 금액이다. totalPrice는 처음 산 금액 그대로 둔다.
    public int getRemainingPrice() {
        return this.items.stream()
                .mapToInt(item -> item.getUnitPrice() * item.getRemainingQuantity())
                .sum();
    }
}
