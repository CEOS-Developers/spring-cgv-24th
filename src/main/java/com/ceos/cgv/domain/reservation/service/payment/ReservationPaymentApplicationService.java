package com.ceos.cgv.domain.reservation.service.payment;

import com.ceos.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos.cgv.domain.payment.dto.request.PaymentOrderCreateRequest;
import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.service.PaymentWorkflowService;
import com.ceos.cgv.domain.payment.service.result.PaymentOrderCreationResult;
import com.ceos.cgv.domain.reservation.service.ReservationService;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class ReservationPaymentApplicationService {
    private final ReservationPaymentOrderService orders;
    private final ReservationService reservationService;
    private final PaymentWorkflowService paymentWorkflow;

    public PaymentOrderCreationResult createOrder(
            Long userId, PaymentOrderCreateRequest request) {
        return orders.createOrder(userId, request);
    }

    public PaymentOperationResponse cancelReservation(Long userId, Long reservationId, UUID key) {
        Optional<Long> paymentId = orders.findPaymentId(userId, reservationId);
        if (paymentId.isEmpty()) {
            reservationService.cancel(reservationId, userId);
            return null;
        }
        if (key == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return paymentWorkflow.cancel(userId, paymentId.get(), key, new PaymentCancelRequest(null, "예매 전체 취소"));
    }
}
