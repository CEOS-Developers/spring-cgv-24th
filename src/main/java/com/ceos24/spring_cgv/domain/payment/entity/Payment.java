package com.ceos24.spring_cgv.domain.payment.entity;

import com.ceos24.spring_cgv.domain.member.entity.Member;
import com.ceos24.spring_cgv.domain.movie.entity.Screening;
import com.ceos24.spring_cgv.domain.payment.enums.OrderType;
import com.ceos24.spring_cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.spring_cgv.domain.payment.enums.TossPaymentMethod;
import com.ceos24.spring_cgv.domain.reservation.entity.Reservation;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

    // 예매는 결제 승인(confirm) 시점에 확정되므로, 결제 요청 시점에는 아직 비어 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(name = "order_id", length = 64, nullable = false)
    private String orderId; // 생성된 고유 주문 번호

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 10, nullable = false)
    private OrderType orderType;

    @Column(name = "order_name", length = 100, nullable = false)
    private String orderName; // 결제창에 표시되는 문구

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount; // 결제 총 금액

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PaymentStatus status; // 결제 상태

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt; // 결제 요청 시각

    // --- 아래는 결제 승인(/payment/confirm) 이후에 채워지는 필드 ---

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 30)
    private TossPaymentMethod method; // 결제 방식

    @Column(name = "approved_at")
    private LocalDateTime approvedAt; // 결제 승인 시각

    @Column(name = "last_transaction_key", length = 64)
    private String lastTransactionKey; // 마지막 거래의 키 값

    // --- 아래는 결제 취소 이후에 채워지는 필드 ---

    @Column(name = "canceled_amount", nullable = false)
    private Integer canceledAmount; // 누적 취소 금액. 남은 금액은 이 값으로 파생한다.

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt; // 전액 취소가 완료된 시각

    @Builder
    private Payment(Member member, Screening screening,
                    String orderId, String orderName, Integer totalAmount) {
        this.member = member;
        this.screening = screening;
        this.orderId = orderId;
        this.orderName = orderName;
        this.totalAmount = totalAmount;
        this.orderType = OrderType.SCREENING;
        this.status = PaymentStatus.READY;
        this.canceledAmount = 0;
        this.requestedAt = LocalDateTime.now();
    }

    /***
     * 함수 기능: 확정된 예매를 결제에 연결한다. 결제 승인 과정에서 좌석을 확정한 직후 호출한다.
     * @param reservation 이 결제로 확정된 예매
     */
    public void linkReservation(Reservation reservation) {
        this.reservation = reservation;
    }

    /***
     * 함수 기능: 토스 승인 응답을 반영해 결제를 완료 상태로 전이한다.
     * @param paymentKey 토스가 발급한 결제 키
     * @param method 결제 수단
     * @param approvedAt 토스가 결제를 승인한 시각
     * @param lastTransactionKey 마지막 거래의 키 값
     */
    public void confirm(String paymentKey, TossPaymentMethod method,
                        LocalDateTime approvedAt, String lastTransactionKey) {

        this.paymentKey = paymentKey;
        this.method = method;
        this.approvedAt = approvedAt;
        this.lastTransactionKey = lastTransactionKey;
        this.status = PaymentStatus.DONE;
    }

    /***
     * 함수 기능: 결제가 실패하여 주문을 중단 상태로 전이한다.
     *          토스가 failUrl로 리다이렉트했을 때 호출한다.
     */
    public void abort() {
        this.status = PaymentStatus.ABORTED;
    }

    /***
     * 함수 기능: 아직 취소되지 않은 남은 결제 금액을 반환한다.
     * @return 총 금액 - 누적 취소 금액
     */
    public Integer getBalanceAmount() {
        return this.totalAmount - this.canceledAmount;
    }

    /***
     * 함수 기능: 토스 취소 응답을 반영해 누적 취소 금액과 결제 상태를 전이한다.
     *          누적 덧셈이 아니라 토스가 알려준 잔액에서 역산하여, PG 원장과의 오차가 쌓이지 않게 한다.
     * @param balanceAmount 토스가 알려준 취소 후 남은 금액
     * @param canceledAt 토스가 취소를 처리한 시각
     */
    public void applyCancel(Integer balanceAmount, LocalDateTime canceledAt) {

        this.canceledAmount = this.totalAmount - balanceAmount;

        if (balanceAmount == 0) {
            this.status = PaymentStatus.CANCELED;
            this.canceledAt = canceledAt;
        } else {
            this.status = PaymentStatus.PARTIAL_CANCELED;
        }
    }
}
