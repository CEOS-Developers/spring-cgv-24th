package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.order.entity.Order;
import com.ceos24.cgv.domain.payment.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findByOrder(Order order);
}
