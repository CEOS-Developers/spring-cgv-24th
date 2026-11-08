package com.ceos24.spring_cgv.domain.payment.service;

import com.ceos24.spring_cgv.domain.payment.dto.CancelAttempt;
import com.ceos24.spring_cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.spring_cgv.domain.payment.enums.CancelStatus;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentCancelRepository;
import com.ceos24.spring_cgv.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/***
 * 취소 시도를 바깥 트랜잭션과 분리해 기록하는 컴포넌트.
 * PaymentService 내부 메서드로 두면 프록시를 거치지 않아 REQUIRES_NEW가 무시되므로 별도 빈으로 둔다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCancelRecorder {

    private final PaymentCancelRepository paymentCancelRepository;
    private final PaymentRepository paymentRepository;

    // 토스가 거절한 뒤 같은 취소를 다시 시도할 때 키에 붙는 세대 구분자.
    private static final String GENERATION_DELIMITER = "#";

    /***
     * 함수 기능: 토스에 취소를 보내기 전에 취소 시도를 별도 트랜잭션으로 기록한다.
     *          바깥 트랜잭션이 롤백되어도 이 기록은 남아, 결과를 모르는 취소가 흔적 없이 사라지지 않는다.
     *          같은 취소(지문 동일)의 미결 시도가 있으면 새 행을 만들지 않고 그 멱등성 키를 그대로 재사용한다.
     * @param paymentId 취소할 결제 식별자
     * @param cancelAmount 취소할 금액
     * @param reason 취소 사유
     * @param fingerprint 어떤 취소인지를 나타내는 지문 (cancel-{주문번호}-{좌석좌표})
     * @return 사용할 취소 시도의 식별자와 멱등성 키
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CancelAttempt record(Long paymentId, Integer cancelAmount, String reason, String fingerprint) {

        // 결과를 모르는 같은 취소가 있으면 그 키를 다시 써서 토스가 원래 결과를 돌려주게 한다.
        // 이게 이중 환불을 막는 지점이다. 새 키를 만들면 토스는 별개의 취소로 처리한다.
        Optional<PaymentCancel> pending = paymentCancelRepository
                .findFirstByPaymentIdAndStatusAndRequestFingerprint(
                        paymentId, CancelStatus.REQUESTED, fingerprint);

        if (pending.isPresent()) {
            PaymentCancel reused = pending.get();

            log.info("[결제 취소 재시도] paymentId={}, 기존 시도를 이어간다. idempotencyKey={}",
                    paymentId, reused.getIdempotencyKey());

            return new CancelAttempt(reused.getId(), reused.getIdempotencyKey());
        }

        String idempotencyKey = nextIdempotencyKey(fingerprint);

        // 바깥 트랜잭션의 엔티티는 이 트랜잭션에서 보이지 않으므로, FK만 연결하는 참조를 새로 만든다.
        PaymentCancel cancel = paymentCancelRepository.save(PaymentCancel.builder()
                .payment(paymentRepository.getReferenceById(paymentId))
                .cancelAmount(cancelAmount)
                .reason(reason)
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint)
                .build());

        log.info("[결제 취소 시도] paymentId={}, cancelAmount={}, idempotencyKey={}",
                paymentId, cancelAmount, idempotencyKey);

        return new CancelAttempt(cancel.getId(), idempotencyKey);
    }

    /***
     * 함수 기능: 토스가 명시적으로 거절한 취소 시도를 실패로 확정한다.
     *          바깥 트랜잭션이 롤백되는 중에도 이 기록은 남겨야 하므로 별도 트랜잭션이다.
     *          응답을 받지 못한 경우에는 호출하지 않고 REQUESTED로 남겨 둔다.
     * @param cancelId 실패로 확정할 취소 시도 식별자
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long cancelId) {

        paymentCancelRepository.findById(cancelId).ifPresent(PaymentCancel::fail);
    }

    /***
     * 함수 기능: 이번 시도에 사용할 멱등성 키를 만든다.
     *          첫 시도는 지문을 그대로 쓰고, 이미 시도한 적이 있으면 세대를 붙인다.
     *          토스가 거절 응답을 키 단위로 보관할 수 있어, 거절 뒤 재시도는 새 키여야 통과한다.
     * @param fingerprint 취소 지문
     * @return 멱등성 키
     */
    private String nextIdempotencyKey(String fingerprint) {

        long attempts = paymentCancelRepository.countByRequestFingerprint(fingerprint);

        return attempts == 0 ? fingerprint : fingerprint + GENERATION_DELIMITER + (attempts + 1);
    }
}
