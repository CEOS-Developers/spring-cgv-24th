package com.ceos24.spring_cgv.domain.payment.exception.code;

import com.ceos24.spring_cgv.global.apipayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements BaseErrorCode {

    PAYMENT_SCREENING_CLOSED(HttpStatus.BAD_REQUEST, "PAYMENT400_1", "이미 상영이 시작된 회차는 예매할 수 없습니다."),
    PAYMENT_SEAT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "PAYMENT400_2", "상영관에 존재하지 않는 좌석입니다."),
    PAYMENT_SEAT_DUPLICATED(HttpStatus.BAD_REQUEST, "PAYMENT400_3", "같은 좌석을 중복해서 선택할 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT400_4", "결제 금액이 일치하지 않습니다."),
    PAYMENT_METHOD_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "PAYMENT400_5", "지원하지 않는 결제 수단입니다."),
    PAYMENT_NOT_CANCELABLE(HttpStatus.BAD_REQUEST, "PAYMENT400_6", "이미 상영이 시작된 회차의 결제는 취소할 수 없습니다."),
    PAYMENT_ALREADY_CANCELED(HttpStatus.BAD_REQUEST, "PAYMENT400_7", "이미 전액 취소된 결제입니다."),
    PAYMENT_SEAT_ALREADY_CANCELED(HttpStatus.BAD_REQUEST, "PAYMENT400_8", "이미 취소된 좌석이 포함되어 있습니다."),
    PAYMENT_CANCEL_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "PAYMENT400_9", "남은 결제 금액보다 많은 금액을 취소할 수 없습니다."),
    PAYMENT_SCREENING_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404_1", "해당 상영회차를 찾을 수 없습니다."),
    PAYMENT_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404_2", "해당 주문을 찾을 수 없습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404_3", "완료된 결제를 찾을 수 없습니다."),
    PAYMENT_CANCEL_SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404_4", "해당 결제에 존재하지 않는 좌석입니다."),
    PAYMENT_SEAT_ALREADY_TAKEN(HttpStatus.CONFLICT, "PAYMENT409_1", "이미 예매된 좌석입니다."),
    PAYMENT_SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "PAYMENT409_2", "다른 고객이 결제 중인 좌석입니다."),
    PAYMENT_CANCEL_IN_PROGRESS(HttpStatus.CONFLICT, "PAYMENT409_3", "결과가 확인되지 않은 취소 요청이 있습니다. 잠시 후 다시 시도해 주세요."),
    PAYMENT_CONFIRM_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT502_1", "결제 승인에 실패했습니다."),
    PAYMENT_CANCEL_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT502_2", "결제 취소에 실패했습니다."),
    PAYMENT_CANCEL_RESULT_UNKNOWN(HttpStatus.BAD_GATEWAY, "PAYMENT502_3", "결제 취소 결과를 확인할 수 없습니다. 고객센터로 문의해 주세요."),
    PAYMENT_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT503_1", "일시적으로 결제를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
