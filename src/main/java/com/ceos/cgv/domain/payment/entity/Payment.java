package com.ceos.cgv.domain.payment.entity;

import com.ceos.cgv.domain.payment.enums.PaymentStatus;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_order_id", nullable = false, unique = true)
    private PaymentOrder order;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "balance_amount", nullable = false)
    private long balanceAmount;

    @Column(name = "active_operation_id")
    private Long activeOperationId;

    private String method;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "receipt_url", length = 2000)
    private String receiptUrl;

    @Column(name = "cancellations_json", columnDefinition = "text")
    private String cancellationsJson;

    @Builder
    public Payment(PaymentOrder order) {
        if (order == null) throw new IllegalArgumentException("Payment order is required");
        this.order = order;
        status = PaymentStatus.READY;
        balanceAmount = order.getTotalAmount();
        cancellationsJson = "[]";
    }

    public void claimApproval(String key, Long operationId) {
        require(
                status == PaymentStatus.READY
                        && activeOperationId == null
                        && key != null
                        && !key.isBlank()
                        && operationId != null);
        paymentKey = key;
        status = PaymentStatus.PROCESSING;
        activeOperationId = operationId;
    }

    public void claimCancellation(Long operationId) {
        require(isCancelable() && activeOperationId == null && operationId != null);
        activeOperationId = operationId;
    }

    public void approve(GatewayPayment result, String cancellations) {
        require(status == PaymentStatus.PROCESSING && activeOperationId != null);
        status = PaymentStatus.DONE;
        copy(result, cancellations);
        activeOperationId = null;
    }

    public void canceled(GatewayPayment result, String cancellations) {
        require(isCancelable() && activeOperationId != null);
        status =
                result.balanceAmount() == 0
                        ? PaymentStatus.CANCELED
                        : PaymentStatus.PARTIALLY_CANCELED;
        copy(result, cancellations);
        activeOperationId = null;
    }

    private void copy(GatewayPayment result, String cancellations) {
        balanceAmount = result.balanceAmount();
        method = result.method();
        approvedAt = result.approvedAt();
        receiptUrl = result.receiptUrl();
        cancellationsJson = cancellations;
    }

    public void failedApproval() {
        require(status == PaymentStatus.PROCESSING && activeOperationId != null);
        status = PaymentStatus.FAILED;
        activeOperationId = null;
    }

    public void failedCancellation() {
        require(isCancelable() && activeOperationId != null);
        activeOperationId = null;
    }

    public void compensate() {
        require(
                (status == PaymentStatus.PROCESSING || status == PaymentStatus.COMPENSATING)
                        && activeOperationId != null);
        status = PaymentStatus.COMPENSATING;
    }

    public void compensated(GatewayPayment result, String cancellations) {
        require(status == PaymentStatus.COMPENSATING && activeOperationId != null);
        copy(result, cancellations);
        status = PaymentStatus.CANCELED;
        activeOperationId = null;
    }

    private boolean isCancelable() {
        return status == PaymentStatus.DONE || status == PaymentStatus.PARTIALLY_CANCELED;
    }

    // 서비스가 업무 입력을 검증한 뒤의 위반은 내부 상태 전이/영속성 불변식 오류다.
    private void require(boolean valid) {
        if (!valid) throw new IllegalStateException("Invalid payment state transition");
    }
}
