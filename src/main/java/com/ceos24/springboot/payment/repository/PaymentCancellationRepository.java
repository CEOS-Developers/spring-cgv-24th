package com.ceos24.springboot.payment.repository;

import com.ceos24.springboot.payment.domain.PaymentCancellation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PaymentCancellationRepository extends JpaRepository<PaymentCancellation, Long> {
    // 결제 락 이후 current read로 읽어 MySQL의 이전 스냅샷을 사용하지 않는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PaymentCancellation c where c.payment.id = :paymentId order by c.id")
    List<PaymentCancellation> findAllWithLock(@Param("paymentId") Long paymentId);

    List<PaymentCancellation> findAllByPayment_IdOrderByIdDesc(Long paymentId);
}
