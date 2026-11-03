package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.PaymentCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentCancellationRepository
        extends JpaRepository<PaymentCancellation, Long> {

    Optional<PaymentCancellation> findByPayment_IdAndStatus(
            Long paymentId,
            PaymentCancellation.Status status
    );
}