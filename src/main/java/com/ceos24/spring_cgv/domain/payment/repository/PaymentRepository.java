package com.ceos24.spring_cgv.domain.payment.repository;

import com.ceos24.spring_cgv.domain.payment.entity.Payment;
import com.ceos24.spring_cgv.domain.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 함수 기능: 주문 번호로 결제를 조회한다. 결제 승인 시 금액 대조에 사용한다.
     * @param orderId 서버가 생성한 주문 번호
     * @return 해당 주문 번호의 결제
     */
    Optional<Payment> findByOrderId(String orderId);

    /**
     * 함수 기능: 회원이 결제한 특정 상태의 결제를 조회한다.
     *          회원 조건을 쿼리에 함께 걸어, 다른 회원의 결제는 애초에 조회되지 않는다.
     * @param id 결제 식별자
     * @param memberId 조회를 요청한 회원 식별자
     * @param status 조회 대상 결제 상태
     * @return 해당 회원의 해당 상태 결제
     */
    Optional<Payment> findByIdAndMemberIdAndStatus(Long id, Long memberId, PaymentStatus status);
}
