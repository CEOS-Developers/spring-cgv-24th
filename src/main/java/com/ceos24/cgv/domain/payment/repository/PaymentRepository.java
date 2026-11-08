package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.order.entity.Order;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findByOrder(Order order);

    Optional<Payment> findTopByReservationOrderByIdDesc(Reservation reservation);
}
