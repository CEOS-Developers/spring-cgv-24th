package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 예매 id를 함께 건다. 소유자 검증은 예매 조회가 하므로, 주문번호가 그 예매의 것이면 곧 본인 주문이다.
    Optional<Payment> findByOrderIdAndReservationId(String orderId, Long reservationId);

    // 구매도 같은 이유로 구매 id를 함께 건다.
    Optional<Payment> findByOrderIdAndPurchaseId(String orderId, Long purchaseId);

    boolean existsByReservationIdAndStatus(Long reservationId, PaymentStatus status);

    Optional<Payment> findByOrderId(String orderId);

    // 결제창만 열고 떠난 주문(READY)은 빼고 본다. 예매 하나에 그런 주문이 여럿 쌓일 수 있다.
    List<Payment> findByReservationIdAndStatusNotOrderByIdDesc(Long reservationId, PaymentStatus excluded);

    Optional<Payment> findByPurchaseId(Long purchaseId);
}
