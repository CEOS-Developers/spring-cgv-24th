package com.ceos24.cgv.domain.concession.entity;

import com.ceos24.cgv.domain.concession.enums.OrderStatus;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "theater_id", nullable = false)
	private Theater theater;

	@Column(nullable = false)
	private Instant orderedAt;

	@Column(nullable = false)
	private Integer totalPrice;

	@Enumerated(EnumType.STRING)
	private OrderStatus status;

	@Column(name = "toss_order_id", unique = true, length = 64)
	private String tossOrderId;

	private Order(User user, Theater theater, Instant orderedAt, Integer totalPrice) {
		this.user = user;
		this.theater = theater;
		this.orderedAt = orderedAt;
		this.totalPrice = totalPrice;
		this.status = OrderStatus.PENDING_PAYMENT;
		this.tossOrderId = UUID.randomUUID().toString();
	}

	public static Order create(User user, Theater theater, Integer totalPrice) {
		return new Order(user, theater, Instant.now(), totalPrice);
	}

	public void completePayment() {
		this.status = OrderStatus.PAID;
	}

	public void cancelPayment() {
		this.status = OrderStatus.CANCELED;
	}
}
