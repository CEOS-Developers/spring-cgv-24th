package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.global.entity.BaseTimeEntity;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// 취소 요청 하나가 행 하나다. 결제에는 누적 취소액만 두고, 어느 요청이 얼마를 언제 돌려줬는지는 여기에 남는다.
// 결과를 모르는 취소를 다시 보내려면 처음 보낸 멱등키·금액·사유가 그대로 있어야 해서 요청 시점에 먼저 저장한다.
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancel extends BaseTimeEntity {

    // 토스 멱등키는 15일간 유효하다. 그 뒤 같은 키로 보내면 새 요청으로 처리되어 두 번 환불될 수 있다.
    private static final int RETRY_DAYS = 14;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_cancel_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // orderId를 쓰지 않는다. 같은 결제에 부분 취소를 두 번 하면 주소·메서드·키가 같아져
    // 두 번째 취소에 첫 번째 응답이 돌아오고, 실제로는 취소되지 않았는데 성공으로 기록된다.
    @Column(nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentCancelStatus status;

    // 재시도 기한의 기준이다. createdAt은 JPA Auditing이 시스템 시계로 채워 서비스의 Clock과 다르다.
    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column(length = 64)
    private String transactionKey;

    private LocalDateTime canceledAt;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 500)
    private String failureMessage;

    // 잔액 검사를 생성 경로에 묶는다. 검사 없이 만든 취소 요청이 토스까지 가는 일이 없다.
    @Builder
    private PaymentCancel(Payment payment, int amount, String reason, LocalDateTime now) {
        payment.verifyCancelable(amount);
        this.payment = payment;
        this.idempotencyKey = UUID.randomUUID().toString().replace("-", "");
        this.amount = amount;
        this.reason = reason;
        this.status = PaymentCancelStatus.IN_PROGRESS;
        this.requestedAt = now;
    }

    public boolean isDone() {
        return this.status == PaymentCancelStatus.DONE;
    }

    // 결제 누적액도 함께 바꾼다. 둘 중 하나만 바뀌면 잔액과 이력이 어긋난다.
    public void complete(String transactionKey, LocalDateTime canceledAt) {
        requireInProgress();
        this.payment.applyCancel(this.amount);
        this.status = PaymentCancelStatus.DONE;
        this.transactionKey = transactionKey;
        this.canceledAt = canceledAt;
    }

    public void fail(String failureCode, String failureMessage) {
        requireInProgress();
        this.status = PaymentCancelStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    // 완료·거절된 취소는 결과가 정해졌다. 다시 취소하려면 새 요청을 만든다.
    public void verifyRetryable(LocalDateTime now) {
        if (this.status != PaymentCancelStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_NOT_RETRYABLE);
        }
        if (!now.isBefore(this.requestedAt.plusDays(RETRY_DAYS))) {
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_RETRY_EXPIRED);
        }
    }

    private void requireInProgress() {
        if (this.status != PaymentCancelStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_NOT_RETRYABLE);
        }
    }
}
