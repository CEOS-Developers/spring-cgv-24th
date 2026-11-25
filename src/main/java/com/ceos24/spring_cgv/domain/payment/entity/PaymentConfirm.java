package com.ceos24.spring_cgv.domain.payment.entity;

import com.ceos24.spring_cgv.domain.payment.enums.ConfirmStatus;
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

/***
 * 토스 승인 시도 1건. 재시도마다 새 행을 남겨, 앞선 시도의 결과(미확인/승인)를 덮어쓰지 않는다.
 */
@Entity
@Table(name = "payment_confirm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentConfirm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "payment_key", length = 200, nullable = false)
    private String paymentKey; // 결과 조회와 환불에 쓰는 토스 결제 키

    @Column(name = "amount", nullable = false)
    private Integer amount; // 승인을 요청한 금액

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ConfirmStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    // --- 아래는 토스 승인 응답을 받은 뒤에 채워지는 필드 ---

    @Column(name = "transaction_key", length = 64)
    private String transactionKey; // 승인 거래의 키

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Builder
    private PaymentConfirm(Payment payment, String paymentKey, Integer amount) {
        this.payment = payment;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.status = ConfirmStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now();
    }

    /***
     * 함수 기능: 토스가 승인했음을 기록한다.
     * @param transactionKey 승인 거래의 키
     * @param approvedAt 토스가 승인한 시각
     */
    public void approve(String transactionKey, LocalDateTime approvedAt) {

        this.transactionKey = transactionKey;
        this.approvedAt = approvedAt;
        this.status = ConfirmStatus.APPROVED;
    }

    /***
     * 함수 기능: 토스가 명시적으로 거절한 승인 시도를 실패로 확정한다.
     *          결과를 모르는 경우에는 호출하지 않고 REQUESTED로 남겨 둔다.
     */
    public void fail() {
        this.status = ConfirmStatus.FAILED;
    }
}
