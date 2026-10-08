package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
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

// 결제 시도 하나가 행 하나다. 토스 orderId는 재사용할 수 없어서, 거절 뒤 다시 결제하면 새 행이 생긴다.
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    // 토스 orderId 규칙: 6~64자, 영문·숫자·-·_. 접두사 6자 + UUID 32자 = 38자.
    private static final String RESERVATION_ORDER_PREFIX = "cgv-r-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    // 예매 id를 그대로 쓰지 않는다. 6자 미만이 되고, 시도마다 새 값이어야 하며, 순차 값이면 남의 주문번호를 짐작할 수 있다.
    @Column(nullable = false, unique = true, length = 64)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    // 승인 요청의 amount를 대조하는 기준이다. successUrl로 돌아오는 금액은 주소창 값이라 조작될 수 있다.
    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    // 승인된 결제만 갖는다. 이후 조회·취소에 쓴다.
    @Column(unique = true, length = 200)
    private String paymentKey;

    @Column(length = 50)
    private String method;

    private LocalDateTime approvedAt;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 500)
    private String failureMessage;

    // 금액을 호출자에게 받지 않는다. 받으면 예매 좌석 가격 합과 다른 값이 기준값이 될 틈이 생긴다.
    @Builder
    private Payment(Reservation reservation) {
        this.orderId = RESERVATION_ORDER_PREFIX + UUID.randomUUID().toString().replace("-", "");
        this.reservation = reservation;
        this.amount = reservation.getTotalPrice();
        this.status = PaymentStatus.READY;
    }

    public void verifyAmount(int requestedAmount) {
        if (requestedAmount != this.amount) {
            throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    // 거절·결과 불명으로 끝난 시도를 다시 승인하지 않는다. 다시 결제하려면 새 주문을 만든다.
    public void startApproval() {
        requireStatus(PaymentStatus.READY);
        this.status = PaymentStatus.IN_PROGRESS;
    }

    public void approve(String paymentKey, String method, LocalDateTime approvedAt) {
        requireStatus(PaymentStatus.IN_PROGRESS);
        this.status = PaymentStatus.DONE;
        this.paymentKey = paymentKey;
        this.method = method;
        this.approvedAt = approvedAt;
    }

    public void fail(String failureCode, String failureMessage) {
        requireStatus(PaymentStatus.IN_PROGRESS);
        this.status = PaymentStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    private void requireStatus(PaymentStatus expected) {
        if (this.status != expected) {
            throw new CustomException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
    }
}
