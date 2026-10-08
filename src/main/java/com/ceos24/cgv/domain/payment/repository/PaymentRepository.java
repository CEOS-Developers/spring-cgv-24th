package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 예매 id를 함께 건다. 소유자 검증은 예매 조회가 하므로, 주문번호가 그 예매의 것이면 곧 본인 주문이다.
    Optional<Payment> findByOrderIdAndReservationId(String orderId, Long reservationId);

    boolean existsByReservationIdAndStatus(Long reservationId, PaymentStatus status);
}
