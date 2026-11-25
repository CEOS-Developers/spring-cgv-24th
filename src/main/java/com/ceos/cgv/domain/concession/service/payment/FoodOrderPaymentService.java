package com.ceos.cgv.domain.concession.service.payment;

import com.ceos.cgv.domain.concession.entity.FoodOrder;
import com.ceos.cgv.domain.concession.entity.Inventory;
import com.ceos.cgv.domain.concession.entity.OrderItem;
import com.ceos.cgv.domain.concession.repository.FoodOrderRepository;
import com.ceos.cgv.domain.concession.repository.InventoryRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class FoodOrderPaymentService {
    private final FoodOrderRepository foodOrderRepository;
    private final InventoryRepository inventoryRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void startPayment(Long orderId, Long userId) {
        FoodOrder order = lockOwned(orderId, userId);
        order.startPayment(clock.instant());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmPayment(Long orderId, Long userId) {
        lockOwned(orderId, userId).confirmPayment();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseFailedPayment(Long orderId, Long userId) {
        FoodOrder order = lockOwned(orderId, userId);
        order.releaseFailedPayment();
        restoreInventory(order);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requirePaid(Long orderId, Long userId) {
        lockOwned(orderId, userId).requirePaid();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelPayment(Long orderId, Long userId) {
        FoodOrder order = lockOwned(orderId, userId);
        order.cancelPayment();
        restoreInventory(order);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void releaseCheckout(Long orderId, Long userId) {
        FoodOrder order = lockOwned(orderId, userId);
        if (order.releaseCheckout(clock.instant())) {
            restoreInventory(order);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean expireIfElapsed(Long orderId) {
        FoodOrder order = foodOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_ORDER_NOT_FOUND));
        if (!order.expireIfElapsed(clock.instant())) {
            return false;
        }
        restoreInventory(order);
        return true;
    }

    private FoodOrder lockOwned(Long orderId, Long userId) {
        FoodOrder order = foodOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_ORDER_NOT_FOUND));
        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return order;
    }

    private void restoreInventory(FoodOrder order) {
        Long cinemaId = order.getCinema().getId();
        var items = order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        for (OrderItem item : items) {
            Inventory inventory = inventoryRepository
                    .findByCinema_IdAndProduct_IdForUpdate(cinemaId, item.getProduct().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
            inventory.restore(item.getQuantity());
        }
    }
}
