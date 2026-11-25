package com.ceos24.spring_cgv.domain.payment.service;

import com.ceos24.spring_cgv.domain.payment.client.dto.TossPaymentConfirmResponse;
import com.ceos24.spring_cgv.domain.payment.entity.PaymentConfirm;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentConfirmRepository;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/***
 * 승인 시도를 바깥 트랜잭션과 분리해 기록하는 컴포넌트.
 * PaymentService 내부 메서드로 두면 프록시를 거치지 않아 REQUIRES_NEW가 무시되므로 별도 빈으로 둔다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConfirmRecorder {

    private final PaymentConfirmRepository paymentConfirmRepository;
    private final PaymentRepository paymentRepository;

    /***
     * 함수 기능: 토스에 승인을 보내기 전에 승인 시도를 별도 트랜잭션으로 기록한다.
     *          FK 검사가 payment 행의 공유 락을 요구하므로, 바깥 트랜잭션이 payment를
     *          변경(flush)하기 전에 호출해야 한다. 아니면 자기 자신의 락을 기다리게 된다.
     * @param paymentId 승인할 결제 식별자
     * @param paymentKey 토스가 발급한 결제 키
     * @param amount 승인을 요청할 금액
     * @return 승인 시도 식별자
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long record(Long paymentId, String paymentKey, Integer amount) {

        // 바깥 트랜잭션의 엔티티를 공유하지 않도록, FK만 연결하는 참조를 새로 만든다.
        PaymentConfirm confirm = paymentConfirmRepository.save(PaymentConfirm.builder()
                .payment(paymentRepository.getReferenceById(paymentId))
                .paymentKey(paymentKey)
                .amount(amount)
                .build());

        log.info("[결제 승인 시도] paymentId={}, confirmId={}, amount={}",
                paymentId, confirm.getId(), amount);

        return confirm.getId();
    }

    /***
     * 함수 기능: 토스가 승인했음을 기록한다. 이후 로컬 처리가 롤백되어도 이 기록은 남는다.
     * @param confirmId 승인 시도 식별자
     * @param result 토스 승인 응답
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markApproved(Long confirmId, TossPaymentConfirmResponse result) {

        paymentConfirmRepository.findById(confirmId).ifPresent(confirm -> confirm.approve(
                result.lastTransactionKey(),
                result.approvedAt().toLocalDateTime()));
    }

    /***
     * 함수 기능: 토스가 명시적으로 거절한 승인 시도를 실패로 확정한다.
     *          결과를 모르는 경우에는 호출하지 않고 REQUESTED로 남겨 둔다.
     * @param confirmId 승인 시도 식별자
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long confirmId) {

        paymentConfirmRepository.findById(confirmId).ifPresent(PaymentConfirm::fail);
    }
}
