package com.ceos24.spring_cgv.domain.payment.repository;

import com.ceos24.spring_cgv.domain.payment.entity.Payment;
import com.ceos24.spring_cgv.domain.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
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
     * @param id 결제 식별자
     * @param memberId 조회를 요청한 회원 식별자
     * @param status 조회 대상 결제 상태
     * @return 해당 회원의 해당 상태 결제
     */
    Optional<Payment> findByIdAndMemberIdAndStatus(Long id, Long memberId, PaymentStatus status);

    /**
     * 함수 기능: 지정한 상태들 중 하나인 본인 결제를 조회한다.
     *          취소 시 상영 시각 검사와 좌석 점유 해제에 필요한 연관을 함께 가져온다.
     * @param id 결제 식별자
     * @param memberId 조회를 요청한 회원 식별자
     * @param statuses 조회 대상 결제 상태 목록
     * @return 해당 회원의 결제
     */
    @EntityGraph(attributePaths = {"screening", "reservation"})
    Optional<Payment> findByIdAndMemberIdAndStatusIn(Long id, Long memberId, Collection<PaymentStatus> statuses);
}
