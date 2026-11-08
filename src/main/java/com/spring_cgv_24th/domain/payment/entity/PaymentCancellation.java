package com.spring_cgv_24th.domain.payment.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payment_cancellation", uniqueConstraints =
        @UniqueConstraint(name = "uk_payment_cancellation_request", columnNames = {"payment_id", "request_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancellation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_cancellation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(nullable = false, updatable = false, length = 36)
    private String requestId;

    @Column(nullable = false, updatable = false, length = 200)
    private String cancelReason;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false)
    private long balanceBefore;

    @Column(nullable = false, updatable = false)
    private boolean fullCancellation;

    // 매점은 상품 ID·수량, 예매는 상영 좌석 ID·1을 저장한다.
    @ElementCollection
    @CollectionTable(name = "payment_cancellation_item",
            joinColumns = @JoinColumn(name = "payment_cancellation_id"))
    @MapKeyColumn(name = "item_id")
    @Column(name = "quantity", nullable = false)
    private Map<Long, Integer> items = new HashMap<>();

    @Column(nullable = false, updatable = false)
    private Instant requestedAt;

    private Instant completedAt;
    private Instant failedAt;

    public PaymentCancellation(Payment payment, String requestId, String cancelReason, long amount,
                               boolean fullCancellation, Map<Long, Integer> items, Instant requestedAt) {
        this.payment = payment;
        this.requestId = requestId;
        this.cancelReason = cancelReason;
        this.amount = amount;
        this.balanceBefore = payment.getBalanceAmount();
        this.fullCancellation = fullCancellation;
        this.items.putAll(items);
        this.requestedAt = requestedAt;
    }

    public void fail(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public void complete(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
