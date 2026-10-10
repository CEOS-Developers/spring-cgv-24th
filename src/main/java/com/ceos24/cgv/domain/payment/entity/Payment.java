package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.global.entity.BaseTimeEntity;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

// 결제 시도 하나가 행 하나다. 토스 orderId는 재사용할 수 없어서, 거절 뒤 다시 결제하면 새 행이 생긴다.
// 예매와 구매 중 정확히 하나에 속한다. 엔티티 생성자가 1차로 막고, CHECK 제약은 엔티티를 거치지 않는 쓰기까지 막는다.
@Getter
@Entity
@Table(check = @CheckConstraint(name = "ck_payment_one_target", constraint =
        "(reservation_id IS NOT NULL AND purchase_id IS NULL) OR (reservation_id IS NULL AND purchase_id IS NOT NULL)"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    // 토스 orderId 규칙: 6~64자, 영문·숫자·-·_. 접두사 6자 + UUID 32자 = 38자.
    private static final String RESERVATION_ORDER_PREFIX = "cgv-r-";
    private static final String PURCHASE_ORDER_PREFIX = "cgv-p-";
    // 토스 승인 호출이 끝나는 데 걸리는 최대 시간(연결 5초 × 2번 + 읽기 60초)보다 넉넉하게 둔다.
    public static final Duration RECOVERY_DELAY = Duration.ofMinutes(2);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    // 예매 id를 그대로 쓰지 않는다. 6자 미만이 되고, 시도마다 새 값이어야 하며, 순차 값이면 남의 주문번호를 짐작할 수 있다.
    @Column(nullable = false, unique = true, length = 64)
    private String orderId;

    // 예매는 거절돼도 선점이 남아 같은 예매로 다시 결제한다. 예매 하나에 결제 시도가 여럿일 수 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    // 매점은 붙잡아 둔 자원이 없어 다시 결제하려면 새 주문을 만든다. 구매 하나에 결제 시도는 하나다.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", unique = true)
    private Purchase purchase;

    // 승인 요청의 amount를 대조하는 기준이다. successUrl로 돌아오는 금액은 주소창 값이라 조작될 수 있다.
    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    // 승인을 시작할 때(T1) 저장한다. 승인 결과를 모르는 결제를 토스에 물어볼 수 있는 유일한 키다.
    // 문서 공용 테스트 키는 orderId 조회가 동작하지 않아, 승인 성공 뒤에 저장하면 결과 불명 결제는 조회할 방법이 없다.
    @Column(unique = true, length = 200)
    private String paymentKey;

    @Column(length = 50)
    private String method;

    // 결과 불명 정리가 원래 요청의 토스 호출과 겹치지 않게 하는 기준이다.
    // updatedAt은 JPA Auditing이 시스템 시계로 채워 서비스의 Clock과 다르다.
    private LocalDateTime approvalStartedAt;

    private LocalDateTime approvedAt;

    // 확정된 취소의 합이다. 이력은 PaymentCancel에 있고, 이 값은 잔액 검사를 행 합산 없이 하려고 둔다.
    @Column(nullable = false)
    private int canceledAmount;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 500)
    private String failureMessage;

    // 금액을 호출자에게 받지 않는다. 받으면 좌석·상품 가격 합과 다른 값이 기준값이 될 틈이 생긴다.
    // 대상이 둘 다 있거나 둘 다 없는 것은 호출 코드의 버그라 서버 오류로 둔다.
    @Builder
    private Payment(Reservation reservation, Purchase purchase) {
        if ((reservation == null) == (purchase == null)) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        String prefix = reservation != null ? RESERVATION_ORDER_PREFIX : PURCHASE_ORDER_PREFIX;
        this.orderId = prefix + UUID.randomUUID().toString().replace("-", "");
        this.reservation = reservation;
        this.purchase = purchase;
        this.amount = reservation != null ? reservation.getTotalPrice() : purchase.getTotalPrice();
        this.status = PaymentStatus.READY;
    }

    public void verifyAmount(int requestedAmount) {
        if (requestedAmount != this.amount) {
            throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    // 거절·결과 불명으로 끝난 시도를 다시 승인하지 않는다. 다시 결제하려면 새 주문을 만든다.
    public void startApproval(String paymentKey, LocalDateTime now) {
        if (this.status == PaymentStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
        requireStatus(PaymentStatus.READY);
        this.status = PaymentStatus.IN_PROGRESS;
        this.paymentKey = paymentKey;
        this.approvalStartedAt = now;
    }

    // paymentKey는 승인 시작에서 이미 저장했다. 토스 응답의 paymentKey가 요청과 같은지는 클라이언트가 확인한다.
    public void approve(String method, LocalDateTime approvedAt) {
        requireStatus(PaymentStatus.IN_PROGRESS);
        this.status = PaymentStatus.DONE;
        this.method = method;
        this.approvedAt = approvedAt;
    }

    // 결과 불명 결제를 정리해도 되는지 본다. 승인을 시작한 지 RECOVERY_DELAY가 지나지 않았으면 원래 요청이
    // 아직 토스를 기다리고 있을 수 있다. 그 요청의 T2와 정리가 함께 결제를 확정하지 않게 한다.
    public void verifyRecoverable(LocalDateTime now) {
        requireStatus(PaymentStatus.IN_PROGRESS);
        if (this.approvalStartedAt == null || now.isBefore(this.approvalStartedAt.plus(RECOVERY_DELAY))) {
            throw new CustomException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
    }

    // paymentKey를 지운다. 승인 요청의 paymentKey는 토스가 확인하기 전의 값이라, 남의 키나 잘못된 키가 거절된 시도에
    // 남아 있으면 유니크 제약 때문에 그 키의 진짜 주문이 승인을 시작하지 못한다.
    public void fail(String failureCode, String failureMessage) {
        requireStatus(PaymentStatus.IN_PROGRESS);
        this.status = PaymentStatus.FAILED;
        this.paymentKey = null;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public int getBalanceAmount() {
        return this.amount - this.canceledAmount;
    }

    // 취소 금액은 서버가 좌석·상품에서 계산하므로 잔액을 넘을 수 없다. 넘는다면 계산 쪽 버그이고,
    // 토스까지 보내면 엉뚱한 금액이 환불되거나 토스의 거절에 기대게 되어 여기서 먼저 막는다.
    public void verifyCancelable(int cancelAmount) {
        if (this.status == PaymentStatus.CANCELED) {
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_CANCELED);
        }
        if (this.status != PaymentStatus.DONE && this.status != PaymentStatus.PARTIAL_CANCELED) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_CANCELABLE);
        }
        if (cancelAmount < 1 || cancelAmount > getBalanceAmount()) {
            throw new CustomException(ErrorCode.CANCEL_AMOUNT_EXCEEDS_BALANCE);
        }
    }

    // PaymentCancel.complete만 부른다. 이력 없이 누적액만 바뀌는 일이 없게 한다.
    void applyCancel(int cancelAmount) {
        verifyCancelable(cancelAmount);
        this.canceledAmount += cancelAmount;
        this.status = getBalanceAmount() == 0 ? PaymentStatus.CANCELED : PaymentStatus.PARTIAL_CANCELED;
    }

    private void requireStatus(PaymentStatus expected) {
        if (this.status != expected) {
            throw new CustomException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
    }
}
