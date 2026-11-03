package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
}
