package com.ceos24.cgv.domain.payment.exception;

import com.ceos24.cgv.global.exception.GeneralException;

public class PaymentException extends GeneralException {

    public PaymentException(PaymentErrorCode errorCode) {
        super(errorCode);
    }
}