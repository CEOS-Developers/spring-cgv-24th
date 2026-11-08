package com.ceos24.spring_cgv.domain.payment.entity;

import com.ceos24.spring_cgv.domain.payment.enums.CancelStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_cancel",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_cancel_idempotency_key",
                columnNames = {"idempotency_key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "cancel_amount", nullable = false)
    private Integer cancelAmount; // 이번 취소 금액

    @Column(name = "reason", length = 200, nullable = false)
    private String reason; // 토스에 보낸 cancelReason

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CancelStatus status;

    // 토스 Idempotency-Key 헤더로 보내는 값. 같은 논리적 취소의 재시도는 같은 키를 쓰므로
    // 토스가 원래 결과를 돌려주고 이중 환불이 일어나지 않는다.
    @Column(name = "idempotency_key", length = 200, nullable = false)
    private String idempotencyKey;

    // 어떤 취소인지를 나타내는 지문(cancel-{주문번호}-{좌석좌표}). 재시도 판별의 기준이다.
    // 토스가 거절한 뒤 재시도하면 키는 세대가 붙어 달라지지만 지문은 그대로다.
    @Column(name = "request_fingerprint", length = 200, nullable = false)
    private String requestFingerprint;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    // --- 아래는 토스 취소 응답을 받은 뒤에 채워지는 필드 ---

    @Column(name = "balance_amount_after")
    private Integer balanceAmountAfter; // 토스가 알려준 취소 후 남은 금액

    @Column(name = "transaction_key", length = 64)
    private String transactionKey; // 이번 취소 거래의 키

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Builder
    private PaymentCancel(Payment payment, Integer cancelAmount, String reason,
                          String idempotencyKey, String requestFingerprint) {
        this.payment = payment;
        this.cancelAmount = cancelAmount;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.status = CancelStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now();
    }

    /***
     * 함수 기능: 토스 취소 응답을 반영해 취소를 확정 상태로 전이한다.
     * @param balanceAmountAfter 토스가 알려준 취소 후 남은 금액
     * @param transactionKey 이번 취소 거래의 키
     * @param canceledAt 토스가 취소를 처리한 시각
     */
    public void complete(Integer balanceAmountAfter, String transactionKey, LocalDateTime canceledAt) {

        this.balanceAmountAfter = balanceAmountAfter;
        this.transactionKey = transactionKey;
        this.canceledAt = canceledAt;
        this.status = CancelStatus.DONE;
    }

    /***
     * 함수 기능: 토스가 명시적으로 거절한 취소 시도를 실패로 확정한다.
     *          응답을 받지 못한 경우에는 호출하지 않고 REQUESTED로 남겨 둔다.
     */
    public void fail() {
        this.status = CancelStatus.FAILED;
    }
}
