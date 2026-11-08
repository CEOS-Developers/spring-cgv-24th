package com.ceos.cgv.domain.payment.repository;

import com.ceos.cgv.domain.payment.entity.PaymentOperation;
import com.ceos.cgv.domain.payment.enums.PaymentOperationType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentOperationRepository extends JpaRepository<PaymentOperation, Long> {
    Optional<PaymentOperation> findByUserIdAndTypeAndRequestKey(
            Long userId, PaymentOperationType type, String requestKey);
}
