package com.ceos.cgv.domain.payment.repository;

import com.ceos.cgv.domain.payment.entity.PaymentOrder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByOrderId(String orderId);

    Optional<PaymentOrder> findByReservationId(Long reservationId);
}
