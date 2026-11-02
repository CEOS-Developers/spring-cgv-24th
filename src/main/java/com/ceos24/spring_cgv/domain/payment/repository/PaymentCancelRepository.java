package com.ceos24.spring_cgv.domain.payment.repository;

import com.ceos24.spring_cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.spring_cgv.domain.payment.enums.CancelStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

    /**
     * 함수 기능: 결과를 확인하지 못한 취소 시도를 조회한다.
     *          이번 요청과 같은 취소인지(지문 비교) 판별해 재시도를 허용할지 결정하는 데 쓴다.
     * @param paymentId 결제 식별자
     * @param status 확인할 취소 상태
     * @return 해당 상태의 취소 시도
     */
    Optional<PaymentCancel> findFirstByPaymentIdAndStatus(Long paymentId, CancelStatus status);

    /**
     * 함수 기능: 같은 논리적 취소의 미결 시도를 조회한다. 있으면 그 멱등성 키를 재사용한다.
     * @param paymentId 결제 식별자
     * @param status 확인할 취소 상태
     * @param requestFingerprint 취소 지문
     * @return 같은 지문의 해당 상태 취소 시도
     */
    Optional<PaymentCancel> findFirstByPaymentIdAndStatusAndRequestFingerprint(
            Long paymentId, CancelStatus status, String requestFingerprint);

    /**
     * 함수 기능: 같은 논리적 취소를 몇 번 시도했는지 센다.
     *          토스가 거절한 뒤 재시도할 때 새 세대 키를 만드는 데 쓴다.
     * @param requestFingerprint 취소 지문
     * @return 해당 지문의 시도 횟수
     */
    long countByRequestFingerprint(String requestFingerprint);

    /**
     * 함수 기능: 취소 시도를 잠금 조회한다.
     *          별도 트랜잭션에서 커밋된 행을 읽어야 하므로, 스냅샷을 우회하는 잠금 읽기를 쓴다.
     *          일반 조회를 쓰면 REPEATABLE READ에서 그 행이 보이지 않는다.
     * @param id 취소 시도 식별자
     * @return 해당 취소 시도
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentCancel> findWithLockById(Long id);
}
