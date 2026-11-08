package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.entity.PaymentCancelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

    // 자동 재시도는 PaymentCancel.verifyRetryable의 기한(요청 후 14일) 안의 취소만 한다.
    Duration AUTO_RETRY_PERIOD = Duration.ofDays(14);

    boolean existsByPaymentIdAndStatus(Long paymentId, PaymentCancelStatus status);

    // 구매 id를 함께 건다. 다른 구매 경로에 이 취소 id를 넣어도 찾지 못한다.
    Optional<PaymentCancel> findByIdAndPaymentPurchaseId(Long id, Long purchaseId);

    // 예매 id를 함께 건다. 소유자 검증은 예매 잠금 조회가 하므로, 그 예매 결제의 취소면 곧 본인 것이다.
    Optional<PaymentCancel> findByIdAndPaymentReservationId(Long id, Long reservationId);

    List<PaymentCancel> findByPaymentIdInOrderByIdAsc(Collection<Long> paymentIds);

    // 결과 불명 취소의 자동 재시도 대상. 재시도 기한이 지난 취소는 같은 키로 보내면 이중 환불이 될 수 있어 빼고,
    // 관리자 대조로 확인한다. 예매를 함께 읽어 소유자 id를 얻는다.
    @Query("""
            SELECT c FROM PaymentCancel c JOIN FETCH c.payment p JOIN FETCH p.reservation
            WHERE c.status = :status AND c.requestedAt <= :requestedBefore AND c.requestedAt > :retryableAfter
            ORDER BY c.id
            """)
    List<PaymentCancel> findReservationCancelsByStatusRequestedBetween(
            @Param("status") PaymentCancelStatus status,
            @Param("retryableAfter") LocalDateTime retryableAfter,
            @Param("requestedBefore") LocalDateTime requestedBefore);

    // 매점 환불의 자동 재시도 대상. 기한은 위와 같다. 매장 환불이라 소유자 id는 필요 없다.
    @Query("""
            SELECT c FROM PaymentCancel c JOIN FETCH c.payment p JOIN FETCH p.purchase
            WHERE c.status = :status AND c.requestedAt <= :requestedBefore AND c.requestedAt > :retryableAfter
            ORDER BY c.id
            """)
    List<PaymentCancel> findPurchaseCancelsByStatusRequestedBetween(
            @Param("status") PaymentCancelStatus status,
            @Param("retryableAfter") LocalDateTime retryableAfter,
            @Param("requestedBefore") LocalDateTime requestedBefore);
}
