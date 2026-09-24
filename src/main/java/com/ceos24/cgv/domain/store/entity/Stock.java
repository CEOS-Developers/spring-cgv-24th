package com.ceos24.cgv.domain.store.entity;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.global.entity.BaseTimeEntity;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 재고는 어떤 시점에도 1 이상이다(운영 정책). 엔티티 메서드가 1차로 지키고,
// CHECK 제약은 엔티티를 거치지 않는 쓰기까지 막는 최종선이다.
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_branch_product",
        columnNames = {"branch_id", "product_id"}),
        check = @CheckConstraint(name = "ck_stock_quantity_min", constraint = "quantity >= 1"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseTimeEntity {

    private static final int MIN_QUANTITY = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Builder
    private Stock(Branch branch, Product product, int quantity) {
        if (quantity < MIN_QUANTITY) {
            throw new CustomException(ErrorCode.INVALID_STOCK_QUANTITY);
        }
        this.branch = branch;
        this.product = product;
        this.quantity = quantity;
    }

    // 음수 차감은 재고를 늘리는 버그라 막는다. 검사를 모두 끝낸 뒤에만 값을 바꿔 실패 시 상태가 그대로다.
    public void decrease(int amount) {
        if (amount < 1) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (amount > availableQuantity()) {
            throw new CustomException(ErrorCode.OUT_OF_STOCK);
        }
        this.quantity -= amount;
    }

    // 최소 재고를 남겨야 하므로 재고 N 중 팔 수 있는 건 N-1이다.
    public int availableQuantity() {
        return quantity - MIN_QUANTITY;
    }

    public boolean isSoldOut() {
        return availableQuantity() < 1;
    }
}
