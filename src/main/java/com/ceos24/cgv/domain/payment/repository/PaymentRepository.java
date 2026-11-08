package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 결제와 예매의 현재 상태를 같은 SELECT로 읽는다. 한쪽 대상이 없는 결제도 포함한다.
    @Query("""
            select p from Payment p
            left join fetch p.purchase
            left join fetch p.reservation
            where p.id = :paymentId
            """)
    Optional<Payment> findWithTargetById(@Param("paymentId") Long paymentId);

    Optional<Payment> findByPurchase_Id(Long purchaseId);

    Optional<Payment> findByReservation_Id(Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.reservation.id = :reservationId")
    Optional<Payment> findByReservationIdForUpdate(@Param("reservationId") Long reservationId);

    @Query("""
            select p.id from Payment p
            where p.reservation.screening.id = :screeningId
              and p.status = :status and p.expiresAt <= :now
            order by p.id
            """)
    List<Long> findExpiredReservationPaymentIds(@Param("screeningId") Long screeningId,
                                               @Param("status") PaymentStatus status,
                                               @Param("now") LocalDateTime now);

    Optional<Payment> findByOrderId(String orderId);

    @Query("select p.id from Payment p where p.orderId = :orderId")
    Optional<Long> findIdByOrderId(@Param("orderId") String orderId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    @Query("""
            select new com.ceos24.cgv.domain.payment.repository.PaymentTargetReference(
                p.targetType, purchase.id, reservation.id, p.payer.id)
            from Payment p
            left join p.purchase purchase
            left join p.reservation reservation
            where p.id = :paymentId
            """)
    Optional<PaymentTargetReference> findTargetById(@Param("paymentId") Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.purchase.id = :purchaseId")
    Optional<Payment> findByPurchaseIdForUpdate(@Param("purchaseId") Long purchaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);
}
