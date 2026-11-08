package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 예매 id를 함께 건다. 소유자 검증은 예매 조회가 하므로, 주문번호가 그 예매의 것이면 곧 본인 주문이다.
    Optional<Payment> findByOrderIdAndReservationId(String orderId, Long reservationId);

    // 구매도 같은 이유로 구매 id를 함께 건다.
    Optional<Payment> findByOrderIdAndPurchaseId(String orderId, Long purchaseId);

    boolean existsByReservationIdAndStatus(Long reservationId, PaymentStatus status);

    Optional<Payment> findByOrderId(String orderId);

    // 결제창만 열고 떠난 주문(READY)은 빼고 본다. 예매 하나에 그런 주문이 여럿 쌓일 수 있다.
    List<Payment> findByReservationIdAndStatusNotOrderByIdDesc(Long reservationId, PaymentStatus excluded);

    Optional<Payment> findByPurchaseId(Long purchaseId);

    Optional<Payment> findFirstByReservationIdAndStatusIn(Long reservationId, Collection<PaymentStatus> statuses);

    // 스케줄러가 결과 불명 결제를 토스에 묻는 기간(승인 시작 기준). 토스 조회가 이만큼 계속 실패하거나 토스에서 직접
    // 취소된 결제는 매분 다시 물어도 풀리지 않는다. 그 뒤는 관리자가 정리 API로 맞춘다.
    // 엔티티 상수로 두지 않은 이유는 PaymentCancelRepository.AUTO_RETRY_PERIOD와 같다.
    Duration AUTO_RECOVERY_PERIOD = Duration.ofDays(1);

    // 결과 불명 정리 대상. 엔티티가 아니라 주문번호만 읽는다. 정리는 건마다 예매 행을 먼저 잠그고 다시 읽어야 해서
    // 여기서 읽은 엔티티는 쓸 수 없다(잠금 조회가 트랜잭션의 첫 쿼리여야 하는 이유는 결제 세션 1 결정 4).
    @Query("""
            SELECT p.orderId FROM Payment p
            WHERE p.status = :status AND p.reservation IS NOT NULL
              AND p.approvalStartedAt > :startedAfter AND p.approvalStartedAt <= :startedBefore
            ORDER BY p.id
            """)
    List<String> findReservationOrderIdsByStatusStartedBetween(@Param("status") PaymentStatus status,
                                                               @Param("startedAfter") LocalDateTime startedAfter,
                                                               @Param("startedBefore") LocalDateTime startedBefore);

    // 자동 정리 기간이 지난 관리 대상이다.
    @Query("""
            SELECT p.orderId FROM Payment p
            WHERE p.status = :status AND p.reservation IS NOT NULL AND p.approvalStartedAt <= :startedBefore
            ORDER BY p.id
            """)
    List<String> findReservationOrderIdsByStatusStartedBefore(@Param("status") PaymentStatus status,
                                                              @Param("startedBefore") LocalDateTime startedBefore);

    // 매점도 같은 이유로 주문번호만 읽는다. 정리는 건마다 구매 행을 먼저 잠근다.
    @Query("""
            SELECT p.orderId FROM Payment p
            WHERE p.status = :status AND p.purchase IS NOT NULL
              AND p.approvalStartedAt > :startedAfter AND p.approvalStartedAt <= :startedBefore
            ORDER BY p.id
            """)
    List<String> findPurchaseOrderIdsByStatusStartedBetween(@Param("status") PaymentStatus status,
                                                            @Param("startedAfter") LocalDateTime startedAfter,
                                                            @Param("startedBefore") LocalDateTime startedBefore);

    @Query("""
            SELECT p.orderId FROM Payment p
            WHERE p.status = :status AND p.purchase IS NOT NULL AND p.approvalStartedAt <= :startedBefore
            ORDER BY p.id
            """)
    List<String> findPurchaseOrderIdsByStatusStartedBefore(@Param("status") PaymentStatus status,
                                                           @Param("startedBefore") LocalDateTime startedBefore);
}
