package com.ceos24.cgv.domain.payment.dto.response;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.enums.PaymentCancelStatus;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;

import java.time.Instant;
import java.util.List;

public record PaymentResponse(
		Long paymentId, String orderId, PaymentStatus status, Integer amount, Integer balanceAmount, String method, Instant approvedAt,
		List<CancelInfo> cancels
) {
	public static PaymentResponse from(Payment payment, List<PaymentCancel> cancels) {
		return new PaymentResponse(payment.getId(), payment.getTossOrderId(), payment.getStatus(), payment.getAmount(),
				payment.getBalanceAmount(), payment.getMethod(), payment.getApprovedAt(),
				cancels.stream().map(CancelInfo::from).toList());
	}

	public record CancelInfo(Integer cancelAmount, String cancelReason, PaymentCancelStatus status, Instant canceledAt) {
		public static CancelInfo from(PaymentCancel cancel) {
			return new CancelInfo(cancel.getCancelAmount(), cancel.getCancelReason(), cancel.getStatus(), cancel.getCanceledAt());
		}
	}
}
