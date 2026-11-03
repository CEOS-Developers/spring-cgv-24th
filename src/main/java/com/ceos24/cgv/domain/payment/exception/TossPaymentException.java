package com.ceos24.cgv.domain.payment.exception;

import com.ceos24.cgv.global.exception.GeneralException;
import lombok.Getter;

@Getter
public class TossPaymentException extends GeneralException {

	private final String tossCode;
	private final String tossMessage;
	private final boolean resultUnknown; // true면 토스에서 실제로 처리됐을 수 있음 → 실패로 단정 금지

	private TossPaymentException(PaymentErrorStatus status, String tossCode, String tossMessage, boolean resultUnknown) {
		super(status);
		this.tossCode = tossCode;
		this.tossMessage = tossMessage;
		this.resultUnknown = resultUnknown;
	}

	public boolean isNotFoundPayment() {
		return "NOT_FOUND_PAYMENT".equals(tossCode);
	}

	public static TossPaymentException rejected(String tossCode, String tossMessage) {
		return new TossPaymentException(PaymentErrorStatus.PG_REJECTED, tossCode, tossMessage, false);
	}

	public static TossPaymentException unknown(String reason) {
		return new TossPaymentException(PaymentErrorStatus.PG_RESULT_UNKNOWN, null, reason, true);
	}
}
