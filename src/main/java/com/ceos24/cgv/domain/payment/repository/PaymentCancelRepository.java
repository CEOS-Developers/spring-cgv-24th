package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.entity.PaymentCancelStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

    boolean existsByPaymentIdAndStatus(Long paymentId, PaymentCancelStatus status);

    // 결제 id를 함께 건다. 소유자 검증은 구매 조회가 하므로, 그 결제의 취소면 곧 본인 것이다.
    Optional<PaymentCancel> findByIdAndPaymentId(Long id, Long paymentId);

    // 예매 id를 함께 건다. 소유자 검증은 예매 잠금 조회가 하므로, 그 예매 결제의 취소면 곧 본인 것이다.
    Optional<PaymentCancel> findByIdAndPaymentReservationId(Long id, Long reservationId);

    List<PaymentCancel> findByPaymentIdInOrderByIdAsc(Collection<Long> paymentIds);
}
