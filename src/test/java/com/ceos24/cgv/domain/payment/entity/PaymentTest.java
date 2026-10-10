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
        payment.startApproval("tgen_test_key", NOW);

        assertThatThrownBy(() -> payment.startApproval("tgen_test_key", NOW))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
    }

    @Test
    void 승인을_시작할_때_paymentKey와_시각을_저장한다() {
        // 결과를 모르는 결제를 토스에 물어볼 수 있는 키다. 승인 성공 뒤에 저장하면 결과 불명 결제는 조회할 수 없다
        Payment payment = Payment.builder().reservation(reservationOf(1)).build();
        payment.startApproval("tgen_test_key", NOW);

        assertThat(payment.getPaymentKey()).isEqualTo("tgen_test_key");
        assertThat(payment.getApprovalStartedAt()).isEqualTo(NOW);
    }

    @Test
    void 거절되면_확인되지_않은_paymentKey를_지운다() {
        // 남겨 두면 유니크 제약 때문에 그 키의 진짜 주문이 승인을 시작하지 못한다
        Payment payment = Payment.builder().reservation(reservationOf(1)).build();
        payment.startApproval("tgen_someone_else", NOW);

        payment.fail("NOT_FOUND_PAYMENT_SESSION", "결제 시간이 만료되었습니다");

        assertThat(payment.getPaymentKey()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void 승인을_시작한_지_2분이_지나기_전에는_정리하지_않는다() {
        // 원래 요청이 아직 토스를 기다리고 있을 수 있다(연결 5초 × 2 + 읽기 60초)
        Payment payment = Payment.builder().reservation(reservationOf(1)).build();
        payment.startApproval("tgen_test_key", NOW);

        assertThatThrownBy(() -> payment.verifyRecoverable(NOW.plusSeconds(119)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
        payment.verifyRecoverable(NOW.plusMinutes(2));
    }

    @Test
    void 승인_중이_아닌_결제는_정리하지_않는다() {
        Payment ready = Payment.builder().reservation(reservationOf(1)).build();
        Payment done = Payment.builder().reservation(reservationOf(1)).build();
        done.startApproval("tgen_test_key", NOW);
        done.approve("카드", NOW);

        assertThatThrownBy(() -> ready.verifyRecoverable(NOW.plusMinutes(5)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS);
        assertThatThrownBy(() -> done.verifyRecoverable(NOW.plusMinutes(5)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS);
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
