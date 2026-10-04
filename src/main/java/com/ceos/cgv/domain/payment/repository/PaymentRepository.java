package com.ceos.cgv.domain.payment.repository;

import com.ceos.cgv.domain.payment.entity.Payment;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrder_OrderId(String orderId);

    Optional<Payment> findByOrder_ReservationId(Long reservationId);

    Optional<Payment> findByOrder_Id(Long orderId);

    @Query("select p.id from Payment p where p.order.orderId=:orderId")
    Optional<Long> findIdByOrderId(String orderId);

    boolean existsByPaymentKey(String paymentKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id=:id")
    Optional<Payment> lockById(Long id);
}
