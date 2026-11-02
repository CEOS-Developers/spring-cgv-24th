package com.ceos24.springboot.payment.repository;

import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 해당 예매에 연결된 가장 최근 결제를 조회
    // 기존 READY 결제를 재사용하면 사용자가 결제 버튼을 반복해서 눌러도 매번 새로운 주문번호가 생성되지 않는다.
    Optional<Payment> findFirstByTargetTypeAndTargetIdOrderByIdDesc(
            PaymentTargetType targetType,
            Long targetId
    );
}