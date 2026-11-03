package com.ceos24.springboot.payment.repository;

import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentTargetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdAndUser_IdAndStatusInAndApprovedAtIsNotNull(
            Long id, Long userId, Collection<PaymentStatus> statuses);

    Page<Payment> findAllByUser_IdAndStatusInAndApprovedAtIsNotNull(
            Long userId, Collection<PaymentStatus> statuses, Pageable pageable);

    @Query("select p.targetType as targetType, p.targetId as targetId from Payment p where p.id = :id and p.user.id = :userId")
    Optional<TargetReference> findTargetById(@Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id and p.user.id = :userId")
    Optional<Payment> findMyPaymentByIdWithLock(@Param("id") Long id, @Param("userId") Long userId);

    Optional<Payment> findByIdAndUser_IdAndStatus(Long id, Long userId, PaymentStatus status);

    Page<Payment> findAllByUser_IdAndStatus(Long userId, PaymentStatus status, Pageable pageable);

    interface TargetReference {
        PaymentTargetType getTargetType();
        Long getTargetId();
    }

    @Query("""
            select p.targetType as targetType, p.targetId as targetId
            from Payment p where p.orderId = :orderId and p.user.id = :userId
            """)
    Optional<TargetReference> findTarget(@Param("orderId") String orderId, @Param("userId") Long userId);

    // 해당 예매에 연결된 가장 최근 결제를 조회
    // 기존 READY 결제를 재사용하면 사용자가 결제 버튼을 반복해서 눌러도 매번 새로운 주문번호가 생성되지 않는다.
    Optional<Payment> findFirstByTargetTypeAndTargetIdOrderByIdDesc(
            PaymentTargetType targetType,
            Long targetId
    );

    // 예매 -> 결제 순서로 잠그기 위해 먼저 예매 ID만 조회하고, 이후 결제 행을 잠급니다.
    @Query("""
            select p.targetId
            from Payment p
            where p.orderId = :orderId
              and p.user.id = :userId
              and p.targetType = :targetType
            """)
    Optional<Long> findTargetId(
            @Param("orderId") String orderId,
            @Param("userId") Long userId,
            @Param("targetType") PaymentTargetType targetType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Payment p
            where p.orderId = :orderId
              and p.user.id = :userId
            """)
    Optional<Payment> findMyPaymentWithLock(
            @Param("orderId") String orderId,
            @Param("userId") Long userId
    );
}
