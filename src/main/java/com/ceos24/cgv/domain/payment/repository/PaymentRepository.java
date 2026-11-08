package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.order.entity.Order;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId")
    Optional<Payment> findByOrderIdWithLock(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentKey = :paymentKey")
    Optional<Payment> findByPaymentKeyWithLock(@Param("paymentKey") String paymentKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order = :order ORDER BY p.id DESC")
    List<Payment> findAllByOrderOrderByIdDescWithLock(@Param("order") Order order);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.reservation = :reservation ORDER BY p.id DESC")
    List<Payment> findAllByReservationOrderByIdDescWithLock(@Param("reservation") Reservation reservation);

    Optional<Payment> findTopByOrderOrderByIdDesc(Order order);

    Optional<Payment> findTopByReservationOrderByIdDesc(Reservation reservation);
}
