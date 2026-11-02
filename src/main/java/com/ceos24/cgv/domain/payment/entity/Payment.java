package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payments")
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    // 한 예매에 결제 정보가 중복 생성되지 않도록 DB에서도 제한한다.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    // 외부 결제 요청에 사용하는 주문번호. 서버에서 생성한다.
    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    // 결제 인증 이후 전달받는 외부 결제 식별자.
    // 결제 준비 단계에는 아직 없으므로 null을 허용한다.
    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    // 서버에서 계산한 최초 주문 금액. 부분 취소 후에도 유지한다.
    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "canceled_amount", nullable = false)
    private Integer canceledAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    private Payment(Reservation reservation) {
        if (reservation == null
                || reservation.getStatus()
                != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException(
                    "결제 대기 상태의 예매가 필요합니다."
            );
        }

        if (reservation.getTotalPrice() == null
                || reservation.getTotalPrice() <= 0) {
            throw new IllegalArgumentException(
                    "결제 금액은 0보다 커야 합니다."
            );
        }

        this.reservation = reservation;
        this.orderId = UUID.randomUUID().toString();
        this.amount = reservation.getTotalPrice();
        this.canceledAmount = 0;
        this.status = PaymentStatus.READY;
    }

    public static Payment create(Reservation reservation) {
        return new Payment(reservation);
    }

    /**
     * 최초 주문 금액을 유지하면서 취소 가능한 잔액을 계산한다.
     * 실제 취소 가능 여부는 결제 상태도 함께 검증해야 한다.
     */
    public int getRemainingAmount() {
        return amount - canceledAmount;
    }
}