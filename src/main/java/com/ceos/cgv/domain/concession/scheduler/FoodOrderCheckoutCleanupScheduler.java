package com.ceos.cgv.domain.concession.scheduler;

import com.ceos.cgv.domain.concession.repository.FoodOrderRepository;
import com.ceos.cgv.domain.concession.repository.FoodOrderRepository.ExpiredCheckout;
import com.ceos.cgv.domain.concession.service.payment.FoodOrderPaymentService;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FoodOrderCheckoutCleanupScheduler {
    private static final PageRequest BATCH = PageRequest.of(0, 100);

    private final FoodOrderRepository foodOrderRepository;
    private final FoodOrderPaymentService foodOrders;
    private final Clock clock;
    private Instant scannedExpiry;
    private Long scannedOrderId;

    @Scheduled(fixedDelayString = "${cgv.food-checkout.cleanup-delay-ms:60000}",
            initialDelayString = "${cgv.food-checkout.cleanup-delay-ms:60000}")
    public synchronized void cleanup() {
        Instant now = clock.instant();
        List<ExpiredCheckout> candidates =
                foodOrderRepository.findExpiredCheckouts(now, scannedExpiry, scannedOrderId, BATCH);
        if (candidates.isEmpty() && scannedExpiry != null) {
            scannedExpiry = null;
            scannedOrderId = null;
            candidates = foodOrderRepository.findExpiredCheckouts(now, null, null, BATCH);
        }
        if (candidates.isEmpty()) {
            return;
        }

        // 실패한 주문은 전체 범위를 순회한 뒤 재시도한다.
        ExpiredCheckout last = candidates.getLast();
        scannedExpiry = last.getExpiresAt();
        scannedOrderId = last.getOrderId();
        for (ExpiredCheckout candidate : candidates) {
            try {
                foodOrders.expireIfElapsed(candidate.getOrderId());
            } catch (RuntimeException exception) {
                ErrorCode errorCode = exception instanceof BusinessException businessException
                        ? businessException.getErrorCode() : ErrorCode.INTERNAL_SERVER_ERROR;
                log.atWarn()
                        .addKeyValue("event", "food_checkout_cleanup_failed")
                        .addKeyValue("orderId", candidate.getOrderId())
                        .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                        .addKeyValue("errorCode", errorCode.name())
                        .log("Could not expire food checkout");
            }
        }
    }
}
