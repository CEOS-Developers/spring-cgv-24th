package com.ceos.cgv.domain.payment.service;

import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentOrderResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentSeatResponse;
import com.ceos.cgv.domain.payment.entity.Payment;
import com.ceos.cgv.domain.payment.entity.PaymentOperation;
import com.ceos.cgv.domain.payment.entity.PaymentOrder;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentResponses {
    private final PaymentJson paymentJson;

    private List<PaymentSeatResponse> seats(PaymentOrder order) {
        return order.getSeats().stream()
                .map(
                        seat ->
                                new PaymentSeatResponse(
                                        seat.getReservedSeatId(),
                                        seat.getSeatRow(),
                                        seat.getSeatNumber(),
                                        seat.getCanceledAt()))
                .toList();
    }

    public PaymentOrderResponse order(Payment payment) {
        PaymentOrder order = payment.getOrder();
        return new PaymentOrderResponse(
                order.getOrderId(),
                payment.getId(),
                order.getReservationId(),
                order.getOrderName(),
                order.getTotalAmount(),
                order.getSeatPrice(),
                order.getExpiresAt(),
                seats(order));
    }

    public PaymentResponse payment(Payment payment) {
        PaymentOrder order = payment.getOrder();
        return new PaymentResponse(
                payment.getId(),
                order.getOrderId(),
                order.getReservationId(),
                payment.getPaymentKey(),
                payment.getStatus(),
                order.getTotalAmount(),
                payment.getBalanceAmount(),
                payment.getMethod(),
                payment.getApprovedAt(),
                payment.getReceiptUrl(),
                paymentJson.cancellations(payment.getCancellationsJson()),
                seats(order));
    }

    public PaymentOperationResponse operation(PaymentOperation operation) {
        return new PaymentOperationResponse(
                operation.getId(),
                operation.getType(),
                operation.getStatus(),
                operation.getPayment().getId(),
                paymentJson.result(operation.getResultJson()),
                operation.getFailureCode(),
                "/api/v1/payment-operations/" + operation.getId());
    }
}
