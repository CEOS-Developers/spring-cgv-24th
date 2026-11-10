package com.ceos24.springboot.payment.repository;

import com.ceos24.springboot.payment.domain.PaymentCancellation;
import com.ceos24.springboot.payment.domain.PaymentCancellationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;

public interface PaymentCancellationRepository extends JpaRepository<PaymentCancellation, Long> {
    @Query("""
            select c.id from PaymentCancellation c
            where c.status in :statuses
              and (c.recovery.nextAttemptAt <= :now
                   or (c.recovery.nextAttemptAt is null and c.createdAt <= :legacyCutoff))
            order by c.recovery.nextAttemptAt, c.id
            """)
    List<Long> findRecoveryIds(@Param("statuses") Collection<PaymentCancellationStatus> statuses,
                              @Param("now") LocalDateTime now,
                              @Param("legacyCutoff") LocalDateTime legacyCutoff, Pageable pageable);

    @Query("select c.payment.id as paymentId, c.payment.user.id as userId from PaymentCancellation c where c.id = :id")
    Optional<RecoveryReference> findRecoveryReference(@Param("id") Long id);

    interface RecoveryReference {
        Long getPaymentId();
        Long getUserId();
    }
    // 결제 락 이후 current read로 읽어 MySQL의 이전 스냅샷을 사용하지 않는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PaymentCancellation c where c.payment.id = :paymentId order by c.id")
    List<PaymentCancellation> findAllWithLock(@Param("paymentId") Long paymentId);

    List<PaymentCancellation> findAllByPayment_IdOrderByIdDesc(Long paymentId);
}
