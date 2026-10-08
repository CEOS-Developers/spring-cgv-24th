package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 9, 0);

    @Test
    void 예매_결제는_좌석_가격_합을_금액으로_갖는다() {
        Payment payment = Payment.builder().reservation(reservationOf(2)).build();

        assertThat(payment.getAmount()).isEqualTo(28000);
        assertThat(payment.getOrderId()).startsWith("cgv-r-").hasSize(38);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void 구매_결제는_상품_가격_합을_금액으로_갖는다() {
        Purchase purchase = Purchase.builder().build();
        purchase.addItem(TestFixtures.product("팝콘", 5000), 2);
        purchase.addItem(TestFixtures.product("콜라", 3000), 1);

        Payment payment = Payment.builder().purchase(purchase).build();

        assertThat(payment.getAmount()).isEqualTo(13000);
        assertThat(payment.getOrderId()).startsWith("cgv-p-").hasSize(38);
    }

    @Test
    void 결제_대상은_예매와_구매_중_정확히_하나다() {
        assertThatThrownBy(() -> Payment.builder().build())
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
        assertThatThrownBy(() -> Payment.builder().reservation(reservationOf(1)).purchase(Purchase.builder().build()).build())
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    void 승인_중인_결제를_다시_시작하면_PAYMENT_IN_PROGRESS() {
        Payment payment = Payment.builder().reservation(reservationOf(1)).build();
        payment.startApproval();

        assertThatThrownBy(payment::startApproval)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
    }

    private Reservation reservationOf(int seatCount) {
        Reservation reservation = TestFixtures.hold(null,
                TestFixtures.screening(null, TestFixtures.movie("범죄도시4"), NOW.plusHours(3), 14000), NOW);
        for (int col = 1; col <= seatCount; col++) {
            reservation.addSeat(1, col, AudienceType.ADULT);
        }
        return reservation;
    }
}
