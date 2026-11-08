package com.ceos24.springboot.shop.service;

import com.ceos24.springboot.shop.domain.StoreOrderStatus;
import com.ceos24.springboot.shop.repository.StoreOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreOrderExpirationScheduler {
    private final Clock clock;
    private final StoreOrderRepository orders;
    private final StoreOrderExpirationService expiration;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expirePendingOrders() {
        for (Long id : orders.findExpiredOrderIds(StoreOrderStatus.PENDING, LocalDateTime.now(clock),
                null, null, PageRequest.of(0, 100))) {
            try {
                expiration.expireOrder(id);
            } catch (RuntimeException e) {
                log.error("매점 주문 만료 처리 실패: orderId={}", id, e);
            }
        }
    }
}
