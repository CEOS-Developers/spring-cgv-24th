package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

	Optional<PaymentCancel> findByIdempotencyKey(String idempotencyKey);

	List<PaymentCancel> findByPaymentIdOrderByIdAsc(Long paymentId);

	@Query("SELECT c FROM PaymentCancel c JOIN FETCH c.payment WHERE c.status IN " +
			"(com.ceos24.cgv.domain.payment.enums.PaymentCancelStatus.REQUESTED, " +
			"com.ceos24.cgv.domain.payment.enums.PaymentCancelStatus.UNKNOWN) " +
			"AND (c.lastRequestedAt IS NULL OR c.lastRequestedAt <= :staleBefore) ORDER BY c.id")
	List<PaymentCancel> findStalePendingCancels(@Param("staleBefore") Instant staleBefore);
}
