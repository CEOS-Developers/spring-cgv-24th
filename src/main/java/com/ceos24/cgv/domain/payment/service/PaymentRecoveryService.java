package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAbortByOrderId(String orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            payment.abort();
            log.info("독립 트랜잭션: 결제 상태를 ABORTED로 기록 완료 (orderId={})", orderId);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailByOrderId(String orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            payment.fail();
            log.info("독립 트랜잭션: 결제 상태를 FAILED로 기록 완료 (orderId={})", orderId);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordManualRecoveryRequired(String orderId, String paymentKey) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            payment.markManualRecoveryRequired(paymentKey);
            log.error(
                    "독립 트랜잭션: 치명적 불일치 감지! 결제 상태를 MANUAL_RECOVERY_REQUIRED로 기록 완료 (orderId={}, paymentKey={})",
                    orderId,
                    paymentKey);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCompensatedAbort(String orderId, String paymentKey) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            payment.abort();
            log.info("독립 트랜잭션: 자동 보상 취소 완료 후 결제 상태를 ABORTED로 기록 완료 (orderId={}, paymentKey={})", orderId, paymentKey);
        });
    }
}
