package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

	private Payment donePayment() {
		Payment payment = Payment.forOrder(order(1L, user(10L), theater(2L), 15_000));
		payment.approve("pk-1", "카드", Instant.now());
		return payment;
	}

	@Test
	void 취소_가능_잔액을_초과하면_예약할_수_없다() {
		Payment payment = donePayment();

		assertThatThrownBy(() -> payment.reserveCancel(15_001))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.CANCEL_AMOUNT_EXCEEDED);
		assertThat(payment.getBalanceAmount()).isEqualTo(15_000);
	}

	@Test
	void 부분_취소_후_잔액을_모두_취소하면_전체_취소_상태가_된다() {
		Payment payment = donePayment();

		payment.reserveCancel(5_000);
		payment.completeCancel(5_000);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
		assertThat(payment.isCancelable()).isTrue();

		payment.reserveCancel(10_000);
		payment.completeCancel(10_000);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(payment.isCancelable()).isFalse();
	}

	@Test
	void 예약한_취소를_복원하면_잔액이_되돌아온다() {
		Payment payment = donePayment();

		payment.reserveCancel(5_000);
		payment.restoreCancel(5_000);

		assertThat(payment.getBalanceAmount()).isEqualTo(15_000);
	}

	@Test
	void 처리_중인_취소는_실제_잔액을_줄이지_않고_취소_가능_금액만_줄인다() {
		Payment payment = donePayment();

		payment.reserveCancel(9_000);
		payment.reserveCancel(6_000);

		assertThat(payment.getBalanceAmount()).isEqualTo(15_000);
		assertThat(payment.getPendingCancelAmount()).isEqualTo(15_000);
		assertThat(payment.getCancelableAmount()).isZero();
		assertThatThrownBy(() -> payment.reserveCancel(1))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(PaymentErrorStatus.CANCEL_AMOUNT_EXCEEDED);
	}

	@Test
	void 함께_예약된_취소_중_하나만_완료되면_전액_취소로_판정하지_않는다() {
		Payment payment = donePayment();
		payment.reserveCancel(9_000);
		payment.reserveCancel(6_000);

		payment.completeCancel(9_000);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
		assertThat(payment.getBalanceAmount()).isEqualTo(6_000);
		assertThat(payment.getPendingCancelAmount()).isEqualTo(6_000);

		payment.restoreCancel(6_000);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
		assertThat(payment.getCancelableAmount()).isEqualTo(6_000);
		assertThat(payment.getCanceledAmount()).isEqualTo(9_000);
	}
}
