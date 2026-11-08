package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
	@Query("UPDATE Payment p SET p.status = :next WHERE p.id = :id AND p.status IN :currents")
	int updateStatusIfMatches(@Param("id") Long id, @Param("currents") List<PaymentStatus> currents, @Param("next") PaymentStatus next);
}
