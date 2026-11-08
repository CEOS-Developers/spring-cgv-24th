package com.ceos24.springboot.payment.dto;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import org.springframework.util.StringUtils;

// 브라우저 → 우리 서버
// 브라우저로부터 전달받은 값을 검증해야 함
public record PaymentConfirmRequest(
        String paymentKey,
        String orderId,
        Long amount
) {
    public void validate() {
        if (!StringUtils.hasText(paymentKey)
                || paymentKey.length() > 200
                || !StringUtils.hasText(orderId)
                || !orderId.matches("[A-Za-z0-9_=-]{6,64}")
                || amount == null
                || amount <= 0) {
            throw new GlobalException(
                    ErrorCode.INVALID_PAYMENT_CONFIRM_REQUEST
            );
        }
    }
}