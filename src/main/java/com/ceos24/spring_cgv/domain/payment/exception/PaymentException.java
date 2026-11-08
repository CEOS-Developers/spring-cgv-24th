package com.ceos24.spring_cgv.domain.payment.exception;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import com.ceos24.spring_cgv.global.apipayload.exception.ProjectException;

public class PaymentException extends ProjectException {
    public PaymentException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
