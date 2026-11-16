package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByOrderId(Long orderId);

	Optional<Payment> findByReservationId(Long reservationId);

	Optional<Payment> findByPaymentKey(String paymentKey);

	@Modifying
	@Query("UPDATE Payment p SET p.status = com.ceos24.cgv.domain.payment.enums.PaymentStatus.EXPIRED " +
			"WHERE p.status = com.ceos24.cgv.domain.payment.enums.PaymentStatus.READY " +
			"AND p.reservation.id IN (SELECT r.id FROM Reservation r WHERE r.id IN :reservationIds " +
			"AND r.status = com.ceos24.cgv.domain.reservation.enums.ReservationStatus.EXPIRED)")
	int expireByExpiredReservationIds(@Param("reservationIds") List<Long> reservationIds);

	Optional<Payment> findByTossOrderId(String tossOrderId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Payment p WHERE p.id = :id")
	Optional<Payment> findByIdForUpdate(@Param("id") Long id);

	@Modifying(clearAutomatically = true)
	@Query("UPDATE Payment p SET p.status = com.ceos24.cgv.domain.payment.enums.PaymentStatus.IN_PROGRESS, " +
			"p.processingStartedAt = :now WHERE p.id = :id AND p.status IN :currents")
	int startProcessing(@Param("id") Long id, @Param("currents") List<PaymentStatus> currents, @Param("now") Instant now);

	// UNKNOWN은 바로, IN_PROGRESS는 처리 제한 시간이 지난 경우에만 선점한다 (시각이 없는 기존 데이터는 오래된 것으로 본다)
	@Modifying(clearAutomatically = true)
	@Query("UPDATE Payment p SET p.status = com.ceos24.cgv.domain.payment.enums.PaymentStatus.IN_PROGRESS, " +
			"p.processingStartedAt = :now WHERE p.id = :id " +
			"AND (p.status = com.ceos24.cgv.domain.payment.enums.PaymentStatus.UNKNOWN " +
			"OR (p.status = com.ceos24.cgv.domain.payment.enums.PaymentStatus.IN_PROGRESS " +
			"AND (p.processingStartedAt IS NULL OR p.processingStartedAt <= :staleBefore)))")
	int startReconcile(@Param("id") Long id, @Param("staleBefore") Instant staleBefore, @Param("now") Instant now);

	@Query("SELECT p.id FROM Payment p WHERE p.status IN (com.ceos24.cgv.domain.payment.enums.PaymentStatus.UNKNOWN, " +
			"com.ceos24.cgv.domain.payment.enums.PaymentStatus.IN_PROGRESS) " +
			"AND (p.processingStartedAt IS NULL OR p.processingStartedAt <= :staleBefore) ORDER BY p.id")
	List<Long> findStaleProcessingIds(@Param("staleBefore") Instant staleBefore);
}
