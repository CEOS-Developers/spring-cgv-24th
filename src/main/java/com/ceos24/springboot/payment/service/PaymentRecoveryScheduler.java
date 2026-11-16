package com.ceos24.springboot.payment.service;

import com.ceos24.springboot.payment.config.TossPaymentProperties;
import com.ceos24.springboot.payment.domain.PaymentCancellationStatus;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.dto.TossPaymentCancelResponse;
import com.ceos24.springboot.payment.dto.TossPaymentConfirmResponse;
import com.ceos24.springboot.payment.repository.PaymentCancellationRepository;
import com.ceos24.springboot.payment.repository.PaymentRepository;
import com.ceos24.springboot.payment.toss.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {
    private final Clock clock;
    private final TossPaymentProperties properties;
    private final PaymentRepository payments;
    private final PaymentCancellationRepository cancellations;
    private final PaymentConfirmTransactionService confirmations;
    private final PaymentCancelTransactionService refunds;
    private final TossPaymentClient toss;

    // NEVER는 수동 호출에서도 PG 호출 중 DB 트랜잭션을 유지하지 못하도록 한다.
    @Transactional(propagation = Propagation.NEVER)
    @Scheduled(fixedDelayString = "${payment.toss.recovery.interval:30s}",
            initialDelayString = "${payment.toss.recovery.interval:30s}", scheduler = "paymentRecoveryTaskScheduler")
    public void recoverPayments() {
        if (!properties.getRecovery().isEnabled()) { return; }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minus(properties.getRecovery().getProcessingTimeout());
        var page = PageRequest.of(0, properties.getRecovery().getBatchSize());
        for (Long id : payments.findRecoveryIds(List.of(PaymentStatus.UNKNOWN, PaymentStatus.PROCESSING), now, cutoff, page)) {
            try {
                confirmations.claimRecovery(id).ifPresent(context -> {
                    TossPaymentConfirmResponse remote = null;
                    try { remote = toss.findByPaymentKey(context.paymentKey()); }
                    catch (RestClientException e) { log.warn("결제 복구 조회 실패: paymentId={}", id); }
                    var result = confirmations.finish(context, remote);
                    if (result.status() == PaymentStatus.UNKNOWN) {
                        log.warn("결제 결과 미확정, 지연 재조회 예정: paymentId={}", id);
                    }
                });
            } catch (RuntimeException e) {
                // 실패한 항목이 배치의 나머지 항목을 막지 않는다. 커밋된 임대 만료 후 재시도한다.
                log.error("결제 복구 실패: paymentId={}", id, e);
            }
        }
        for (Long id : cancellations.findRecoveryIds(List.of(PaymentCancellationStatus.UNKNOWN,
                PaymentCancellationStatus.PROCESSING), now, cutoff, page)) {
            try {
                refunds.claimRecovery(id).ifPresent(context -> {
                    TossPaymentCancelResponse remote = null;
                    try { remote = toss.findForCancellation(context.paymentKey()); }
                    catch (RestClientException e) { log.warn("취소 복구 조회 실패: cancellationId={}", id); }
                    var result = refunds.finish(context, remote, false);
                    if (result.cancelStatus() == PaymentCancellationStatus.UNKNOWN) {
                        log.warn("취소 결과 미확정, 지연 재조회 예정: cancellationId={}", id);
                    }
                });
            } catch (RuntimeException e) {
                log.error("취소 복구 실패: cancellationId={}", id, e);
            }
        }
    }
}
