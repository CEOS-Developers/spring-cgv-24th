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
                                PaymentSeatResponse.builder()
                                        .reservedSeatId(seat.getReservedSeatId())
                                        .seatRow(seat.getSeatRow())
                                        .seatNumber(seat.getSeatNumber())
                                        .canceledAt(seat.getCanceledAt())
                                        .build())
                .toList();
    }

    public PaymentOrderResponse order(Payment payment) {
        PaymentOrder order = payment.getOrder();
        return PaymentOrderResponse.builder()
                .orderId(order.getOrderId())
                .paymentId(payment.getId())
                .reservationId(order.getReservationId())
                .orderName(order.getOrderName())
                .totalAmount(order.getTotalAmount())
                .seatPrice(order.getSeatPrice())
                .expiresAt(order.getExpiresAt())
                .seats(seats(order))
                .build();
    }

    public PaymentResponse payment(Payment payment) {
        PaymentOrder order = payment.getOrder();
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(order.getOrderId())
                .reservationId(order.getReservationId())
                .paymentKey(payment.getPaymentKey())
                .status(payment.getStatus())
                .activeOperationId(payment.getActiveOperationId())
                .totalAmount(order.getTotalAmount())
                .balanceAmount(payment.getBalanceAmount())
                .method(payment.getMethod())
                .approvedAt(payment.getApprovedAt())
                .receiptUrl(payment.getReceiptUrl())
                .cancellations(paymentJson.cancellations(payment.getCancellationsJson()))
                .seats(seats(order))
                .build();
    }

    public PaymentOperationResponse operation(PaymentOperation operation) {
        return PaymentOperationResponse.builder()
                .operationId(operation.getId())
                .type(operation.getType())
                .status(operation.getStatus())
                .paymentId(operation.getPayment().getId())
                .result(paymentJson.result(operation.getResultJson()))
                .failureCode(operation.getFailureCode())
                .operationUrl("/api/v1/payment-operations/" + operation.getId())
                .build();
    }
}
