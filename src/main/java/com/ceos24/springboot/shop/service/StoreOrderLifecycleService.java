package com.ceos24.springboot.shop.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.PaymentTargetType;
import com.ceos24.springboot.payment.repository.PaymentRepository;
import com.ceos24.springboot.shop.domain.OrderItem;
import com.ceos24.springboot.shop.domain.StoreOrder;
import com.ceos24.springboot.shop.repository.OrderItemRepository;
import com.ceos24.springboot.shop.repository.StoreInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class StoreOrderLifecycleService {
    private final OrderItemRepository items;
    private final StoreInventoryRepository inventories;
    private final PaymentRepository payments;

    // Caller must already hold the order lock. State and stock commit together.
    public void cancelLocked(StoreOrder order) {
        if (order.cancel()) {
            closePendingPayment(order, false);
            restoreStock(order);
        }
    }

    public void expireLocked(StoreOrder order, LocalDateTime now) {
        if (order.expireIfPending(now)) {
            closePendingPayment(order, true);
            restoreStock(order);
        }
    }

    public void failPaymentLocked(StoreOrder order, LocalDateTime now) {
        order.failPayment(now);
        restoreStock(order);
    }

    public void refundLocked(StoreOrder order) {
        order.cancelAfterRefund();
        restoreStock(order);
    }

    private void closePendingPayment(StoreOrder order, boolean expired) {
        payments.findFirstByTargetTypeAndTargetIdOrderByIdDesc(PaymentTargetType.STORE_ORDER, order.getOrderId())
                .ifPresent(existing -> payments.findMyPaymentWithLock(existing.getOrderId(), order.getUser().getId())
                        .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_NOT_FOUND))
                        .closePending(expired));
    }

    private void restoreStock(StoreOrder order) {
        // Every stock-changing flow locks inventories in ascending menu ID order.
        for (OrderItem item : items.findAllByOrderOrderByMenu_MenuIdAsc(order)) {
            inventories.findWithLock(order.getTheater().getTheaterId(), item.getMenu().getMenuId())
                    .orElseThrow(() -> new GlobalException(ErrorCode.STORE_INVENTORY_NOT_FOUND))
                    .increaseStock(item.getQuantity());
        }
    }
}
