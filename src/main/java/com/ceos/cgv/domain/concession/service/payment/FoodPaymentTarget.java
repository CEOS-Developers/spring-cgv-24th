package com.ceos.cgv.domain.concession.service.payment;

import com.ceos.cgv.domain.payment.entity.PaymentOrder;
import com.ceos.cgv.domain.payment.enums.PaymentTargetType;
import com.ceos.cgv.domain.payment.service.target.PaymentTarget;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FoodPaymentTarget implements PaymentTarget {
    private final FoodOrderPaymentService foodOrders;

    @Override
    public PaymentTargetType targetType() {
        return PaymentTargetType.FOOD_ORDER;
    }

    @Override
    public void beginApproval(PaymentOrder order, Long userId) {
        foodOrders.startPayment(order.getFoodOrderId(), userId);
    }

    @Override
    public void completeApproval(PaymentOrder order, Long userId) {
        foodOrders.confirmPayment(order.getFoodOrderId(), userId);
    }

    @Override
    public void releaseFailedApproval(PaymentOrder order, Long userId) {
        foodOrders.releaseFailedPayment(order.getFoodOrderId(), userId);
    }

    @Override
    public List<Long> selectCancellationItems(PaymentOrder order, List<Long> requestedItemIds) {
        if (requestedItemIds != null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return List.of();
    }

    @Override
    public long cancellationAmount(PaymentOrder order, Long userId, List<Long> itemIds) {
        foodOrders.requirePaid(order.getFoodOrderId(), userId);
        return order.getTotalAmount();
    }

    @Override
    public void completeCancellation(PaymentOrder order, Long userId, List<Long> itemIds) {
        foodOrders.cancelPayment(order.getFoodOrderId(), userId);
    }

    @Override
    public FinalizationFailure classifyFinalizationFailure(ErrorCode code) {
        // 승인 요청을 시작한 주문은 만료 대상에서 제외된다. 예외는 복구 조회로 다시 확인한다.
        return FinalizationFailure.PENDING;
    }

    @Override
    public String approvalCompensationReason() {
        return "매점 주문 결제 확정 실패 보상 취소";
    }
}
