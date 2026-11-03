package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.exception.GeneralException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.ceos24.cgv.domain.reservation.entity.Reservation;

import java.util.UUID;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payments")
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", unique = true)
	private Order order;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reservation_id", unique = true)
	private Reservation reservation;

	@Column(nullable = false, unique = true, length = 64)
	private String tossOrderId; // 토스에 전달하는 주문번호

	@Column(nullable = false, length = 100)
	private String orderName; // 결제창에 표시할 상품명

	@Column(unique = true, length = 200)
	private String paymentKey;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(nullable = false)
	private Integer amount; //최초 결제 금액

	@Column(nullable = false)
	private Integer balanceAmount; //취소 가능한 잔액

	private String method; //결제수단

	private Instant approvedAt;

	private Payment(Order order, Reservation reservation, String orderName, Integer amount) {
		this.order = order;
		this.reservation = reservation;
		this.tossOrderId = UUID.randomUUID().toString();
		this.orderName = orderName;
		this.amount = amount;
		this.balanceAmount = amount;
		this.status = PaymentStatus.READY;
	}

	public static Payment forOrder(Order order) {
		return new Payment(order, null, "CGV 매점 주문 #" + order.getId(), order.getTotalPrice());
	}

	public static Payment forReservation(Reservation reservation, String orderName) {
		return new Payment(null, reservation, orderName, reservation.getTotalPrice());
	}

	public boolean isOwnedBy(Long userId) {
		User owner = (order != null) ? order.getUser() : reservation.getUser();
		return owner.getId().equals(userId);
	}

	public boolean isApproved() {
		return status == PaymentStatus.DONE;
	}

	// 결제 1건의 승인은 논리적으로 한 번뿐이므로 토스 주문번호에서 고정된 멱등키를 만든다 (재시도해도 같은 키)
	public String getConfirmIdempotencyKey() {
		return "confirm-" + tossOrderId;
	}

	// 승인 후 주문 확정에 실패해 자동 취소할 때 쓰는 멱등키 (재시도해도 같은 키)
	public String getCompensationIdempotencyKey() {
		return "compensate-" + tossOrderId;
	}

	public void approve(String paymentKey, String method, Instant approvedAt) {
		this.paymentKey = paymentKey;
		this.method = method;
		this.approvedAt = approvedAt;
		this.status = PaymentStatus.DONE;
	}

	public boolean isCancelable() {
		return status == PaymentStatus.DONE || status == PaymentStatus.PARTIAL_CANCELED;
	}

	public void reserveCancel(int amount) {
		if (amount <= 0 || amount > balanceAmount) {
			throw new GeneralException(PaymentErrorStatus.CANCEL_AMOUNT_EXCEEDED);
		}
		this.balanceAmount -= amount;
	}

	public void restoreCancel(int amount) {
		this.balanceAmount += amount;
	}

	public void applyCanceledStatus() {
		this.status = (balanceAmount == 0) ? PaymentStatus.CANCELED : PaymentStatus.PARTIAL_CANCELED;
	}

	public void cancelFully() {
		this.status = PaymentStatus.CANCELED;
		this.balanceAmount = 0;
	}

	public void fail() {
		this.status = PaymentStatus.FAILED;
	}

	public void markUnknown() {
		this.status = PaymentStatus.UNKNOWN;
	}
}
