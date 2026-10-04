package com.ceos.cgv.domain.payment.service;

import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentOrderResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos.cgv.domain.payment.entity.Payment;
import com.ceos.cgv.domain.payment.entity.PaymentOperation;
import com.ceos.cgv.domain.payment.repository.PaymentOperationRepository;
import com.ceos.cgv.domain.payment.repository.PaymentRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {
    private final PaymentRepository paymentRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final PaymentResponses paymentResponses;

    public PaymentOrderResponse order(Long userId, String orderId) {
        Payment payment =
                paymentRepository
                        .findByOrder_OrderId(orderId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
        owned(payment, userId);
        return paymentResponses.order(payment);
    }

    public PaymentResponse payment(Long userId, Long paymentId) {
        Payment payment =
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        owned(payment, userId);
        return paymentResponses.payment(payment);
    }

    public PaymentOperationResponse operation(Long userId, Long operationId) {
        PaymentOperation operation =
                paymentOperationRepository
                        .findById(operationId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.PAYMENT_OPERATION_NOT_FOUND));
        if (!operation.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        return paymentResponses.operation(operation);
    }

    public Optional<Long> paymentIdForReservation(Long userId, Long reservationId) {
        return paymentRepository
                .findByOrder_ReservationId(reservationId)
                .map(
                        payment -> {
                            owned(payment, userId);
                            return payment.getId();
                        });
    }

    public static void owned(Payment payment, Long userId) {
        if (!payment.getOrder().getUserId().equals(userId))
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
}
