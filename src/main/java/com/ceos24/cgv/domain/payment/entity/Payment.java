package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.order.entity.Order;
import com.ceos24.cgv.domain.order.entity.OrderItem;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String paymentKey;

    @Column(name = "order_code", unique = true, nullable = false)
    private String orderId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Long balanceAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String method;

    private LocalDateTime approvedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentCancelHistory> cancelHistories = new ArrayList<>();

    protected Payment(Order order, String orderId, Long totalAmount) {
        this.order = order;
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.balanceAmount = totalAmount;
        this.status = PaymentStatus.READY;
        this.createdAt = LocalDateTime.now();
    }

    protected Payment(Reservation reservation, String orderId, Long totalAmount) {
        this.reservation = reservation;
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.balanceAmount = totalAmount;
        this.status = PaymentStatus.READY;
        this.createdAt = LocalDateTime.now();
    }

    public static Payment createReady(Order order, String orderId, Long amount) {
        validateCreate(order, orderId, amount);
        return new Payment(order, orderId, amount);
    }

    public static Payment createReady(Reservation reservation, String orderId, Long amount) {
        validateCreate(reservation, orderId, amount);
        return new Payment(reservation, orderId, amount);
    }

    private static void validateCreate(Order order, String orderId, Long amount) {
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "주문 정보는 필수입니다.");
        }
        validateCommonCreate(orderId, amount, order.getTotalPrice(), "주문 금액");
    }

    private static void validateCreate(Reservation reservation, String orderId, Long amount) {
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND, "예매 정보는 필수입니다.");
        }
        validateCommonCreate(orderId, amount, reservation.getTotalPrice(), "예매 금액");
    }

    private static void validateCommonCreate(String orderId, Long amount, Long targetPrice, String priceLabel) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("주문 번호(orderId)는 필수입니다.");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("결제 요청 금액은 0보다 커야 합니다.");
        }
        if (!amount.equals(targetPrice)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "결제 요청 금액(" + amount + "원)이 " + priceLabel + "(" + targetPrice + "원)과 일치하지 않습니다.");
        }
    }

    public boolean isReservationPayment() {
        return this.reservation != null;
    }

    public void complete(String paymentKey, String method, LocalDateTime approvedAt) {
        if (this.status != PaymentStatus.READY) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "결제 준비(READY) 상태에서만 승인이 가능합니다. 현재 상태: " + this.status.getDescription());
        }
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("PG 결제 키(paymentKey)는 필수입니다.");
        }

        this.paymentKey = paymentKey;
        this.method = method;
        this.approvedAt = approvedAt != null ? approvedAt : LocalDateTime.now();
        this.status = PaymentStatus.DONE;
    }

    public void cancel(Long cancelAmount, String cancelReason, LocalDateTime canceledAt) {
        cancel(null, null, null, null, cancelAmount, cancelReason, canceledAt);
    }

    public void cancel(
            OrderItem orderItem,
            Long cancelQuantity,
            String itemName,
            Long cancelAmount,
            String cancelReason,
            LocalDateTime canceledAt) {
        cancel(null, orderItem, cancelQuantity, itemName, cancelAmount, cancelReason, canceledAt);
    }

    public void cancel(
            String cancelRequestId,
            OrderItem orderItem,
            Long cancelQuantity,
            String itemName,
            Long cancelAmount,
            String cancelReason,
            LocalDateTime canceledAt) {
        if (this.status == PaymentStatus.CANCELED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CANCELED, "이미 전액 취소된 결제입니다.");
        }
        if (this.status != PaymentStatus.DONE && this.status != PaymentStatus.PARTIAL_CANCELED) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "승인 완료된 결제만 취소할 수 있습니다. 현재 상태: " + this.status.getDescription());
        }
        if (cancelAmount == null || cancelAmount <= 0) {
            throw new IllegalArgumentException("취소 요청 금액은 0보다 커야 합니다.");
        }
        if (cancelAmount > this.balanceAmount) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED,
                    "취소 요청 금액(" + cancelAmount + "원)이 취소 가능 잔액(" + this.balanceAmount + "원)을 초과했습니다.");
        }

        this.balanceAmount -= cancelAmount;
        if (this.balanceAmount == 0) {
            this.status = PaymentStatus.CANCELED;
        } else {
            this.status = PaymentStatus.PARTIAL_CANCELED;
        }

        PaymentCancelHistory cancelHistory = new PaymentCancelHistory(
                this, cancelRequestId, orderItem, cancelQuantity, itemName, cancelAmount, cancelReason, canceledAt);
        this.cancelHistories.add(cancelHistory);
    }

    public void abort() {
        if (this.status == PaymentStatus.READY) {
            this.status = PaymentStatus.ABORTED;
        }
    }

    public void fail() {
        if (this.status == PaymentStatus.READY) {
            this.status = PaymentStatus.FAILED;
        }
    }

    public void markManualRecoveryRequired(String paymentKey) {
        if (paymentKey != null && !paymentKey.isBlank()) {
            this.paymentKey = paymentKey;
        }
        this.status = PaymentStatus.MANUAL_RECOVERY_REQUIRED;
    }

    public List<PaymentCancelHistory> getCancelHistories() {
        return Collections.unmodifiableList(cancelHistories);
    }
}
