package com.ceos24.springboot.payment.dto;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import org.springframework.util.StringUtils;

public record PaymentCancelRequest(String cancelReason, Long cancelAmount) {
    public void validate(String requestId) {
        if (!StringUtils.hasText(cancelReason) || cancelReason.length() > 200
                || (cancelAmount != null && cancelAmount <= 0)
                || requestId == null || !requestId.matches("[a-z0-9_-]{1,64}")) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_CANCEL_REQUEST);
        }
    }
}
