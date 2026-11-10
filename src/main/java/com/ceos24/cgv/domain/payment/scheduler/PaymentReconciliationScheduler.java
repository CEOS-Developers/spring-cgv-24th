package com.ceos24.cgv.domain.payment.scheduler;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.payment.service.PaymentService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 웹훅이 누락/거부되어 IN_PROGRESS로 남은 결제를 토스 단건 조회로 수렴시키는 보정 작업. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private static final long STUCK_THRESHOLD_MINUTES = 10L;

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 300000)
    public void reconcileStuckPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<Payment> stuck = paymentRepository.findAllByStatusAndCreatedAtBefore(PaymentStatus.IN_PROGRESS, threshold);
        for (Payment payment : stuck) {
            try {
                paymentService.reconcileStuckPayment(payment.getOrderId());
            } catch (Exception e) {
                log.error("승인 중 결제 보정 실패: orderId={}, error={}", payment.getOrderId(), e.getMessage(), e);
            }
        }
    }
}
