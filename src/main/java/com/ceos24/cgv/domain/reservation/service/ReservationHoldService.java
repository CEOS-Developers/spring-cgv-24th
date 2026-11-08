package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.payment.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReservationHoldService {
    private final PaymentRepository payments;
    private final PaymentTransactionService transactions;

    // 조회/새 예매 트랜잭션의 첫 단계에서 정리한다. 외부 PG 호출이나 별도 DB 연결은 사용하지 않는다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void expireForScreening(Long screeningId) {
        LocalDateTime now = LocalDateTime.now();
        for (Long paymentId : payments.findExpiredReservationPaymentIds(screeningId, PaymentStatus.READY, now)) {
            transactions.expireUnprocessedReservation(paymentId, now);
        }
    }
}
