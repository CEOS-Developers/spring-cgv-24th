package com.spring_cgv_24th.domain.payment.entity;

import com.spring_cgv_24th.domain.payment.enums.PaymentStatus;
import com.spring_cgv_24th.domain.payment.enums.PaymentTargetType;
import com.spring_cgv_24th.domain.payment.target.PaymentTarget;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "payment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_target", columnNames = {"target_type", "target_id"}),
        @UniqueConstraint(name = "uk_payment_key", columnNames = "payment_key")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false)
    private PaymentTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(name = "payment_order_id", nullable = false, updatable = false, length = 64)
    private String paymentOrderId;

    @Column(name = "payment_key", nullable = false, updatable = false,
            columnDefinition = "varchar(200) character set utf8mb4 collate utf8mb4_bin")
    private String paymentKey;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    private String method;
    private Instant approvedAt;
    private long balanceAmount;

    @Column(length = 200)
    private String cancelReason;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<PaymentCancellation> cancellations = new ArrayList<>();

    public Payment(PaymentTargetType targetType, PaymentTarget target, String paymentKey, long amount) {
        this.targetType = targetType;
        this.targetId = target.getId();
        this.paymentOrderId = target.getPaymentOrderId();
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.status = PaymentStatus.PROCESSING;
    }

    public void complete(String method, Instant approvedAt, long balanceAmount) {
        status = PaymentStatus.COMPLETED;
        this.method = method;
        this.approvedAt = approvedAt;
        this.balanceAmount = balanceAmount;
    }

    public void fail() {
        status = PaymentStatus.FAILED;
    }

    public void markUnknown() {
        if (status != PaymentStatus.CANCEL_REJECTED_VERIFY) {
            status = status.isCancellationPending() ? PaymentStatus.CANCEL_UNKNOWN : PaymentStatus.UNKNOWN;
        }
    }

    public void beginCancellation(String reason) {
        cancelReason = reason;
        status = PaymentStatus.CANCEL_PROCESSING;
    }

    public void verifyCancellationRejection() {
        status = PaymentStatus.CANCEL_REJECTED_VERIFY;
    }

    public void rejectCancellation() {
        status = balanceAmount == amount ? PaymentStatus.COMPLETED : PaymentStatus.PARTIALLY_CANCELLED;
        cancelReason = cancellations.reversed().stream()
                .filter(cancellation -> cancellation.getCompletedAt() != null)
                .map(PaymentCancellation::getCancelReason)
                .findFirst().orElse(null);
    }

    public void cancel(long balanceAmount) {
        status = balanceAmount == 0 ? PaymentStatus.CANCELLED : PaymentStatus.PARTIALLY_CANCELLED;
        this.balanceAmount = balanceAmount;
    }

    public void addCancellation(PaymentCancellation cancellation) {
        cancellations.add(cancellation);
    }

    public PaymentCancellation getLatestCancellation() {
        return cancellations.isEmpty() ? null : cancellations.getLast();
    }
}
