package com.ceos24.springboot.payment.service.target;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentTargetType;
import com.ceos24.springboot.payment.dto.PaymentConfirmResponse;
import com.ceos24.springboot.shop.domain.StoreOrder;
import com.ceos24.springboot.shop.domain.StoreOrderStatus;
import com.ceos24.springboot.shop.service.StoreOrderLifecycleService;

import java.time.LocalDateTime;

record StoreOrderPaymentTarget(StoreOrder order, StoreOrderLifecycleService lifecycle) implements PaymentTarget {
    @Override
    public PaymentTargetType type() {
        return PaymentTargetType.STORE_ORDER;
    }

    @Override
    public Long id() {
        return order.getOrderId();
    }

    @Override
    public Long amount() {
        Integer amount = order.getTotalAmount();
        return amount == null ? null : amount.longValue();
    }

    @Override
    public boolean isCompleted() {
        return order.getStatus() == StoreOrderStatus.PAID;
    }

    @Override
    public boolean isHeld() {
        return order.getStatus() == StoreOrderStatus.PENDING && order.isPaymentProcessing();
    }

    @Override
    public ErrorCode expiredError() {
        return ErrorCode.STORE_ORDER_EXPIRED;
    }

    @Override
    public void beginPayment(LocalDateTime now) {
        order.beginPayment(now);
    }

    @Override
    public void completePayment() {
        order.completePayment();
    }

    @Override
    public void failPayment(LocalDateTime now) {
        lifecycle.failPaymentLocked(order, now);
    }

    @Override
    public void validatePaid() {
        if (!isCompleted() || order.isPaymentProcessing()) {
            throw new GlobalException(ErrorCode.PAYMENT_NOT_CANCELABLE);
        }
    }

    @Override
    public void validateRefund(LocalDateTime now) {
        validatePaid();
    }

    @Override
    public void refund() {
        lifecycle.refundLocked(order);
    }

    @Override
    public PaymentConfirmResponse response(Payment payment) {
        return PaymentConfirmResponse.from(payment, order);
    }
}
