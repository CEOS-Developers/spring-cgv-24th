package com.ceos24.spring_cgv.domain.payment.exception.code;

import com.ceos24.spring_cgv.global.apipayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentSuccessCode implements BaseSuccessCode {

    PAYMENT_READY(HttpStatus.OK, "PAYMENT200_1", "결제 요청 정보가 성공적으로 등록되었습니다."),
    PAYMENT_CONFIRMED(HttpStatus.OK, "PAYMENT200_2", "결제가 성공적으로 승인되었습니다."),
    PAYMENT_FAILED(HttpStatus.OK, "PAYMENT200_3", "결제가 실패하여 주문이 중단 처리되었습니다."),
    PAYMENT_FETCHED(HttpStatus.OK, "PAYMENT200_4", "결제 정보가 성공적으로 조회되었습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
