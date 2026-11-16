package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.PaymentCancelHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCancelHistoryRepository extends JpaRepository<PaymentCancelHistory, Long> {

    Optional<PaymentCancelHistory> findByCancelRequestId(String cancelRequestId);

    boolean existsByTransactionKey(String transactionKey);

    Optional<PaymentCancelHistory> findByTransactionKey(String transactionKey);
}
