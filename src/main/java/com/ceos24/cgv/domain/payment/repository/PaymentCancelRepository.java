package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

	Optional<PaymentCancel> findByIdempotencyKey(String idempotencyKey);

	List<PaymentCancel> findByPaymentIdOrderByIdAsc(Long paymentId);
}
