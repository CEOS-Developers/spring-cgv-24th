package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.entity.PaymentCancelStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

    boolean existsByPaymentIdAndStatus(Long paymentId, PaymentCancelStatus status);

    // 구매 id를 함께 건다. 다른 구매 경로에 이 취소 id를 넣어도 찾지 못한다.
    Optional<PaymentCancel> findByIdAndPaymentPurchaseId(Long id, Long purchaseId);

    // 예매 id를 함께 건다. 소유자 검증은 예매 잠금 조회가 하므로, 그 예매 결제의 취소면 곧 본인 것이다.
    Optional<PaymentCancel> findByIdAndPaymentReservationId(Long id, Long reservationId);

    List<PaymentCancel> findByPaymentIdInOrderByIdAsc(Collection<Long> paymentIds);
}
