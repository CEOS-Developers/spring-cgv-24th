package com.ceos.cgv.domain.concession.service;

import com.ceos.cgv.domain.concession.dto.FoodOrderCreateRequest;
import com.ceos.cgv.domain.concession.entity.FoodOrder;
import com.ceos.cgv.domain.payment.entity.Payment;
import com.ceos.cgv.domain.payment.entity.PaymentOrder;
import com.ceos.cgv.domain.payment.repository.PaymentOrderRepository;
import com.ceos.cgv.domain.payment.repository.PaymentRepository;
import com.ceos.cgv.domain.payment.service.PaymentResponses;
import com.ceos.cgv.domain.payment.service.result.PaymentOrderCreationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FoodCheckoutService {
    private final FoodOrderService foodOrders;
    private final PaymentOrderRepository paymentOrders;
    private final PaymentRepository payments;
    private final PaymentResponses responses;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentOrderCreationResult checkout(FoodOrderCreateRequest request, UUID key) {
        FoodOrder foodOrder = foodOrders.createCheckout(request, key);
        return paymentOrders.findByFoodOrderId(foodOrder.getId())
                .map(existing -> new PaymentOrderCreationResult(false,
                        responses.order(payments.findByOrder_Id(existing.getId()).orElseThrow())))
                .orElseGet(() -> openPayment(foodOrder));
    }

    private PaymentOrderCreationResult openPayment(FoodOrder foodOrder) {
        PaymentOrder order = paymentOrders.save(PaymentOrder.forFoodOrder(
                UUID.randomUUID().toString(), foodOrder.getId(), foodOrder.getUser().getId(),
                "매점 주문 " + foodOrder.getId(), foodOrder.getTotalPrice(), foodOrder.getHoldExpiresAt()));
        Payment payment = payments.saveAndFlush(Payment.builder().order(order).build());
        return new PaymentOrderCreationResult(true, responses.order(payment));
    }
}
