package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.PaymentCancellation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentCancellationRepository
        extends JpaRepository<PaymentCancellation, Long> {

    Optional<PaymentCancellation> findByPayment_IdAndStatus(
            Long paymentId,
            PaymentCancellation.Status status
    );

    //같은 결제에 대한 두 요청이 차례로 잠금을 획득할 때 최신 취소 상태를 읽도록 함
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select c from PaymentCancellation c
        where c.payment.id = :paymentId and c.idempotencyKey = :key
        """)
    Optional<PaymentCancellation> findRequestForUpdate(
            @Param("paymentId") Long paymentId,
            @Param("key") String key
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PaymentCancellation c where c.id = :id")
    Optional<PaymentCancellation> findByIdForUpdate(@Param("id") Long id);
}