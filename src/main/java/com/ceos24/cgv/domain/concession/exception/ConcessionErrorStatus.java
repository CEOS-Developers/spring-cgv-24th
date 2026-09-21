package com.ceos24.cgv.domain.concession.exception;

import com.ceos24.cgv.global.apiPayload.code.BaseErrorCode;
import com.ceos24.cgv.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ConcessionErrorStatus implements BaseErrorCode {

    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "ITEM404", "존재하지 않는 매점 상품입니다."),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "STOCK404", "해당 영화관에 등록되지 않은 상품입니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "STOCK409", "재고가 부족합니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER404", "존재하지 않는 주문입니다."),
    NOT_ORDER_OWNER(HttpStatus.FORBIDDEN, "ORDER403", "본인의 주문만 조회할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonDTO getReason() {
        return ErrorReasonDTO.builder().message(message).code(code).isSuccess(false).build();
    }

    @Override
    public ErrorReasonDTO getReasonHttpStatus() {
        return ErrorReasonDTO.builder().message(message).code(code).isSuccess(false).httpStatus(httpStatus).build();
    }
}
