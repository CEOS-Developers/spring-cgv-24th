package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.PaymentOperation;
import com.ceos24.cgv.domain.payment.entity.PaymentOperationStatus;
import com.ceos24.cgv.domain.payment.entity.PaymentOperationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentOperationRepository extends JpaRepository<PaymentOperation, Long> {

    Optional<PaymentOperation> findByPayment_IdAndTypeAndIdempotencyKey(
            Long paymentId, PaymentOperationType type, String idempotencyKey);

    List<PaymentOperation> findAllByPayment_IdOrderByRequestedAtAscIdAsc(Long paymentId);

    @Query("select operation.payment.id from PaymentOperation operation where operation.id = :operationId")
    Optional<Long> findPaymentIdById(@Param("operationId") Long operationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select operation from PaymentOperation operation where operation.id = :operationId")
    Optional<PaymentOperation> findByIdForUpdate(@Param("operationId") Long operationId);

    boolean existsByPgTransactionKeyAndIdNot(String pgTransactionKey, Long operationId);

    // 결제 행을 먼저 잠근 서비스 트랜잭션에서 호출한다. RR에서도 미완료 작업의 최신 상태를 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select operation from PaymentOperation operation
            where operation.payment.id = :paymentId and operation.status in :statuses
            order by operation.id
            """)
    List<PaymentOperation> findAllByPaymentIdAndStatusInForUpdate(
            @Param("paymentId") Long paymentId,
            @Param("statuses") Collection<PaymentOperationStatus> statuses);
}
