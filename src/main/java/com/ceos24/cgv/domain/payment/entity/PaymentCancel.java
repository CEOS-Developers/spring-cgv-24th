package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.payment.enums.PaymentCancelStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_cancel")
public class PaymentCancel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_id", nullable = false)
	private Payment payment;

	@Column(nullable = false, unique = true, length = 300)
	private String idempotencyKey;

	private Integer requestedAmount; // 최초 요청에 담긴 금액. null이면 전액 취소 요청

	@Column(nullable = false)
	private Integer cancelAmount;

	@Column(nullable = false, length = 200)
	private String cancelReason;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private PaymentCancelStatus status;

	private String transactionKey;

	private Instant canceledAt;

	private PaymentCancel(Payment payment, String idempotencyKey, Integer requestedAmount, Integer cancelAmount,
						  String cancelReason) {
		this.payment = payment;
		this.idempotencyKey = idempotencyKey;
		this.requestedAmount = requestedAmount;
		this.cancelAmount = cancelAmount;
		this.cancelReason = cancelReason;
		this.status = PaymentCancelStatus.REQUESTED;
	}

	public static PaymentCancel request(Payment payment, String idempotencyKey, Integer requestedAmount, Integer cancelAmount,
										String cancelReason) {
		return new PaymentCancel(payment, idempotencyKey, requestedAmount, cancelAmount, cancelReason);
	}

	public boolean isSameRequest(Long paymentId, Integer requestedAmount, String cancelReason) {
		return payment.getId().equals(paymentId) && Objects.equals(this.requestedAmount, requestedAmount)
				&& this.cancelReason.equals(cancelReason);
	}

	public boolean isPending() {
		return status == PaymentCancelStatus.REQUESTED || status == PaymentCancelStatus.UNKNOWN;
	}

	public void complete(String transactionKey, Instant canceledAt) {
		this.transactionKey = transactionKey;
		this.canceledAt = canceledAt;
		this.status = PaymentCancelStatus.DONE;
	}

	public void fail() {
		this.status = PaymentCancelStatus.FAILED;
	}

	public void markUnknown() {
		this.status = PaymentCancelStatus.UNKNOWN;
	}
}
