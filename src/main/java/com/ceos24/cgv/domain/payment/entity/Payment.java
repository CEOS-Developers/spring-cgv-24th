package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
	@JoinColumn(name = "order_id", nullable = false, unique = true)
	private Order order;

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

	private Payment(Order order) {
		this.order = order;
		this.amount = order.getTotalPrice();
		this.balanceAmount = order.getTotalPrice();
		this.status = PaymentStatus.READY;
	}

	public static Payment create(Order order) {
		return new Payment(order);
	}
}
