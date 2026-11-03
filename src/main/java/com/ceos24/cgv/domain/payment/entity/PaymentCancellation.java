package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.payment.exception.PaymentException;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorCode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_cancellations")
public class PaymentCancellation extends BaseTimeEntity {

    public enum Status {
        REQUESTED,  // 취소 요청을 저장했고 결과를 기다리는 상태
        DONE,       // Toss 취소 성공을 확인한 상태
        FAILED      // 취소되지 않았음을 확인한 상태
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 부분 취소까지 고려하면 결제 한 건에 취소 기록이 여러 개 생길 수 있다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false, length = 200)
    private String reason;

    // 같은 취소 건을 Toss에 재전송할 때 이 키를 재사용한다.
    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    // Toss가 취소 성공 시 내려주는 거래 식별자와 시각
    @Column(unique = true)
    private String transactionKey;

    private OffsetDateTime canceledAt;

    public static PaymentCancellation requestFull(Payment payment, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new PaymentException(PaymentErrorCode.INVALID_CANCEL_REASON);
        }

        PaymentCancellation cancellation = new PaymentCancellation();
        cancellation.payment = payment;
        cancellation.amount = payment.getRemainingAmount();
        cancellation.reason = reason;
        cancellation.idempotencyKey = UUID.randomUUID().toString();
        cancellation.status = Status.REQUESTED;
        return cancellation;
    }

    public void complete(String transactionKey, OffsetDateTime canceledAt) {
        if (status != Status.REQUESTED) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_CANCELABLE);
        }

        //환불거래를 식별하는 값 추가
        this.transactionKey = transactionKey;
        this.canceledAt = canceledAt;
        this.status = Status.DONE;
    }

    //부분 취소 관련
    public static PaymentCancellation requestPartial(
            Payment payment,
            List<ReservationSeat> seats,
            String reason,
            UUID requestId
    ) {
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new PaymentException(PaymentErrorCode.INVALID_CANCEL_REASON);
        }

        if (requestId == null || seats == null || seats.isEmpty()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_CANCELABLE);
        }

        // 현재 상영 가격이 아니라 각 좌석의 예매 당시 가격으로 환불액을 계산한다.
        int amount = seats.stream()
                .mapToInt(ReservationSeat::getSeatPrice)
                .sum();

        if (amount <= 0 || amount > payment.getRemainingAmount()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_CANCELABLE);
        }

        PaymentCancellation cancellation = new PaymentCancellation();
        cancellation.payment = payment;
        cancellation.amount = amount;
        cancellation.reason = reason;
        cancellation.idempotencyKey = requestId.toString();
        cancellation.status = Status.REQUESTED;

        return cancellation;
    }
}