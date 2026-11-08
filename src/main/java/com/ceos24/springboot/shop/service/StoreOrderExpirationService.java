package com.ceos24.springboot.shop.service;

import com.ceos24.springboot.shop.domain.StoreOrderStatus;
import com.ceos24.springboot.shop.repository.StoreOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class StoreOrderExpirationService {
    private final Clock clock;
    private final StoreOrderRepository orders;
    private final StoreOrderLifecycleService lifecycle;
    private final TransactionTemplate transaction;

    public StoreOrderExpirationService(StoreOrderRepository orders, StoreOrderLifecycleService lifecycle,
                                       PlatformTransactionManager manager, Clock clock) {
        this.clock = clock;
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void expireOrder(Long orderId) {
        transaction.executeWithoutResult(ignored -> orders.findByIdWithLock(orderId)
                .ifPresent(order -> lifecycle.expireLocked(order, LocalDateTime.now(clock))));
    }

    public void releaseExpiredForTheater(Long theaterId) {
        release(null, theaterId);
    }

    public void releaseExpiredForUser(Long userId) {
        release(userId, null);
    }

    private void release(Long userId, Long theaterId) {
        var ids = transaction.execute(ignored -> orders.findExpiredOrderIds(
                StoreOrderStatus.PENDING, LocalDateTime.now(clock), userId, theaterId, PageRequest.of(0, 100)));
        for (Long id : ids) {
            expireOrder(id);
        }
    }
}
