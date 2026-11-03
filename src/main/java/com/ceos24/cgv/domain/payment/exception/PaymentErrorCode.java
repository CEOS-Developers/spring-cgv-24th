package com.ceos24.cgv.domain.payment.exception;

import com.ceos24.cgv.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PaymentErrorCode implements BaseErrorCode {

    PAYMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PAYMENT4041",
            "존재하지 않는 결제 정보입니다."
    ),

    PAYMENT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "PAYMENT4031",
            "해당 결제에 접근할 권한이 없습니다."
    ),

    PAYMENT_AMOUNT_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PAYMENT4001",
            "주문 금액과 요청 금액이 일치하지 않습니다."
    ),

    INVALID_PAYMENT_KEY(
            HttpStatus.BAD_REQUEST,
            "PAYMENT4002",
            "유효하지 않은 결제 키입니다."
    ),

    PAYMENT_NOT_APPROVABLE(
            HttpStatus.CONFLICT,
            "PAYMENT4091",
            "현재 결제 상태에서는 승인을 시작할 수 없습니다."
    ),

    RESERVATION_NOT_PAYABLE(
            HttpStatus.CONFLICT,
            "PAYMENT4092",
            "결제 가능한 예매 상태가 아닙니다."
    ),

    PAYMENT_WINDOW_CLOSED(
            HttpStatus.CONFLICT,
            "PAYMENT4093",
            "상영이 시작되어 결제할 수 없습니다."
    ),

    PAYMENT_NOT_DIRECTLY_CANCELABLE(
            HttpStatus.CONFLICT,
            "PAYMENT4094",
            "현재 결제 상태에서는 미결제 예매 취소를 할 수 없습니다."
    ),
    INVALID_APPROVAL_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "PAYMENT5021",
            "결제 승인 응답을 주문 정보와 대조할 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}