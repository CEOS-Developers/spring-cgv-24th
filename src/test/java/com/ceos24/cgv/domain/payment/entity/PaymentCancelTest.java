package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentCancelTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 9, 0);

    @Test
    void 부분_취소가_쌓이면_잔액이_줄고_잔액이_0이_되면_전액_취소다() {
        Payment payment = approvedPayment(3);   // 42000

        cancel(payment, 14000).complete("tx_1", NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        assertThat(payment.getCanceledAmount()).isEqualTo(14000);
        assertThat(payment.getBalanceAmount()).isEqualTo(28000);

        cancel(payment, 28000).complete("tx_2", NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getBalanceAmount()).isZero();
    }

    @Test
    void 잔액보다_큰_취소는_요청을_만들_수_없다() {
        Payment payment = approvedPayment(2);   // 28000
        cancel(payment, 14000).complete("tx_1", NOW);

        assertThatThrownBy(() -> cancel(payment, 14001))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANCEL_AMOUNT_EXCEEDS_BALANCE);
        assertThatThrownBy(() -> cancel(payment, 0))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANCEL_AMOUNT_EXCEEDS_BALANCE);
    }

    @Test
    void 확정_전에_다른_취소가_잔액을_줄였으면_확정도_막힌다() {
        // 요청 시점 검사만으로는 두 요청이 같은 잔액을 보고 함께 통과할 수 있다. 확정에서 한 번 더 막는다
        Payment payment = approvedPayment(2);
        PaymentCancel first = cancel(payment, 28000);
        PaymentCancel second = cancel(payment, 28000);
        first.complete("tx_1", NOW);

        assertThatThrownBy(() -> second.complete("tx_2", NOW))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ALREADY_CANCELED);
        assertThat(payment.getCanceledAmount()).isEqualTo(28000);
    }

    @Test
    void 승인되지_않은_결제는_취소할_수_없다() {
        Payment ready = Payment.builder().reservation(reservationOf(1)).build();

        assertThatThrownBy(() -> cancel(ready, 14000))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_CANCELABLE);
    }

    @Test
    void 거절된_취소는_잔액을_바꾸지_않는다() {
        Payment payment = approvedPayment(1);
        PaymentCancel cancel = cancel(payment, 14000);

        cancel.fail("NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다.");

        assertThat(cancel.getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getBalanceAmount()).isEqualTo(14000);
    }

    @Test
    void 취소_요청마다_멱등키가_다르다() {
        // 같은 결제의 부분 취소에 같은 키를 쓰면 두 번째 취소에 첫 응답이 돌아와 취소되지 않은 채 성공으로 기록된다
        Payment payment = approvedPayment(2);

        assertThat(cancel(payment, 14000).getIdempotencyKey())
                .isNotEqualTo(cancel(payment, 14000).getIdempotencyKey())
                .hasSize(32);
    }

    @Test
    void 재시도는_진행_중인_취소만_요청_후_14일_전까지_된다() {
        PaymentCancel cancel = cancel(approvedPayment(1), 14000);

        cancel.verifyRetryable(NOW.plusDays(14).minusSeconds(1));
        assertThatThrownBy(() -> cancel.verifyRetryable(NOW.plusDays(14)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_RETRY_EXPIRED);

        cancel.complete("tx_1", NOW);
        assertThatThrownBy(() -> cancel.verifyRetryable(NOW))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_NOT_RETRYABLE);
    }

    private PaymentCancel cancel(Payment payment, int amount) {
        return PaymentCancel.builder().payment(payment).amount(amount).reason("예매 부분 취소: A1").now(NOW).build();
    }

    private Payment approvedPayment(int seatCount) {
        Payment payment = Payment.builder().reservation(reservationOf(seatCount)).build();
        payment.startApproval("tgen_test_key", NOW);
        payment.approve("카드", NOW);
        return payment;
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
