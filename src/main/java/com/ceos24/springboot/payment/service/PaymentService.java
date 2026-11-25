package com.ceos24.springboot.payment.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.dto.PaymentPrepareResponse;
import com.ceos24.springboot.payment.dto.ReservationPaymentPrepareRequest;
import com.ceos24.springboot.payment.dto.StoreOrderPaymentPrepareRequest;
import com.ceos24.springboot.shop.service.StoreOrderExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final StoreOrderExpirationService storeOrderExpirationService;
    private final PaymentPrepareTransactionService transactions;

    @Transactional(propagation = Propagation.NEVER)
    public PaymentPrepareResponse prepareStoreOrder(Long userId, StoreOrderPaymentPrepareRequest request) {
        Long storeOrderId = request.storeOrderId();
        if (storeOrderId == null || storeOrderId <= 0) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_TARGET_ID);
        }
        storeOrderExpirationService.releaseExpiredForUser(userId);
        return transactions.prepareStoreOrder(userId, storeOrderId);
    }

    public PaymentPrepareResponse prepareReservation(Long userId, ReservationPaymentPrepareRequest request) {
        return transactions.prepareReservation(userId, request);
    }
}
