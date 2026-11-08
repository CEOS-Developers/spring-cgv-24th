package com.ceos24.spring_cgv.domain.payment.repository;

import com.ceos24.spring_cgv.domain.payment.entity.PaymentSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentSeatRepository extends JpaRepository<PaymentSeat, Long> {

    /**
     * 함수 기능: 해당 결제에 속한 좌석을 조회한다. 결제 승인 후 예매 확정에 사용한다.
     * @param paymentId 결제 식별자
     * @return 해당 결제의 좌석 목록
     */
    List<PaymentSeat> findAllByPaymentId(Long paymentId);
}
