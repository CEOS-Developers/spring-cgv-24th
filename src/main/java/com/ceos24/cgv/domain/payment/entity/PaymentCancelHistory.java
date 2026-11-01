package com.ceos24.cgv.domain.payment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment_cancel_history")
public class PaymentCancelHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false)
    private Long cancelAmount;

    @Column(nullable = false)
    private String cancelReason;

    @Column(nullable = false)
    private LocalDateTime canceledAt;

    public PaymentCancelHistory(Payment payment, Long cancelAmount, String cancelReason, LocalDateTime canceledAt) {
        if (payment == null) {
            throw new IllegalArgumentException("결제 정보는 필수입니다.");
        }
        if (cancelAmount == null || cancelAmount <= 0) {
            throw new IllegalArgumentException("취소 금액은 0보다 커야 합니다.");
        }
        if (cancelReason == null || cancelReason.isBlank()) {
            throw new IllegalArgumentException("취소 사유는 필수입니다.");
        }
        this.payment = payment;
        this.cancelAmount = cancelAmount;
        this.cancelReason = cancelReason;
        this.canceledAt = canceledAt != null ? canceledAt : LocalDateTime.now();
    }
}
