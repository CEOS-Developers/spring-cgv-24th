package com.ceos24.cgv.domain.store.entity;

import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseRefundTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 9, 0);

    @Test
    void 환불은_남은_수량을_줄이고_처음_산_수량과_금액은_그대로_둔다() {
        Purchase purchase = paidPurchase();
        PurchaseProduct popcorn = purchase.getItems().getFirst();

        purchase.completeRefund(List.of(detail(popcorn, 1)), NOW);

        assertThat(popcorn.getQuantity()).isEqualTo(3);
        assertThat(popcorn.getRemainingQuantity()).isEqualTo(2);
        assertThat(purchase.getTotalPrice()).isEqualTo(21000);
        assertThat(purchase.getRemainingPrice()).isEqualTo(16000);
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PAID);
    }

    @Test
    void 모든_수량이_환불되면_구매가_취소된다() {
        Purchase purchase = paidPurchase();
        PurchaseProduct popcorn = purchase.getItems().get(0);
        PurchaseProduct cola = purchase.getItems().get(1);

        purchase.completeRefund(List.of(detail(popcorn, 3), detail(cola, 2)), NOW);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(purchase.getCancelledAt()).isEqualTo(NOW);
        assertThat(purchase.getRemainingPrice()).isZero();
    }

    @Test
    void 확정할_때도_남은_수량을_넘으면_막는다() {
        // 요청 시점 검사만으로는 두 환불이 같은 남은 수량을 보고 함께 통과할 수 있다
        Purchase purchase = paidPurchase();
        PurchaseProduct popcorn = purchase.getItems().getFirst();
        purchase.completeRefund(List.of(detail(popcorn, 2)), NOW);

        assertThatThrownBy(() -> purchase.completeRefund(List.of(detail(popcorn, 2)), NOW))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANCEL_QUANTITY_EXCEEDED);
        assertThat(popcorn.getRemainingQuantity()).isEqualTo(1);
    }

    @Test
    void 결제가_끝난_구매만_환불할_수_있다() {
        Purchase pending = Purchase.builder().build();
        pending.addItem(TestFixtures.product("팝콘", 5000), 1);

        assertThatThrownBy(pending::verifyRefundable)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PURCHASE_NOT_PAID);
    }

    private Purchase paidPurchase() {
        Purchase purchase = Purchase.builder().build();
        purchase.addItem(TestFixtures.product("팝콘", 5000), 3);
        purchase.addItem(TestFixtures.product("콜라", 3000), 2);
        purchase.complete(NOW);
        return purchase;
    }

    private PurchaseProductCancel detail(PurchaseProduct item, int quantity) {
        return PurchaseProductCancel.builder().purchaseProduct(item).quantity(quantity).build();
    }
}
