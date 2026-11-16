package com.ceos.cgv.domain.payment.repository;

import com.ceos.cgv.domain.payment.entity.PaymentOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByOrderId(String orderId);

    @Query("select o from PaymentOrder o where o.reservationSnapshot.reservationId = :reservationId")
    Optional<PaymentOrder> findByReservationId(Long reservationId);
}
