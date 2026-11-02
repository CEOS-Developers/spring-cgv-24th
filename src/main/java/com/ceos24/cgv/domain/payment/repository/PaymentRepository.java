package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByReservation_Id(Long reservationId);
}