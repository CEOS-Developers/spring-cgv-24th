package com.ceos24.cgv.domain.payment.exception;

import com.ceos24.cgv.global.apiPayload.code.BaseErrorCode;
import com.ceos24.cgv.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PaymentErrorStatus implements BaseErrorCode {

	PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404", "존재하지 않는 결제 정보입니다."),
	PG_REJECTED(HttpStatus.BAD_REQUEST, "PAYMENT400", "결제사에서 요청이 거절되었습니다."),
	NOT_PAYMENT_OWNER(HttpStatus.FORBIDDEN, "PAYMENT403", "본인의 결제만 처리할 수 있습니다."),
	PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT400_1", "결제 금액이 주문 금액과 일치하지 않습니다."),
	PAYMENT_NOT_CONFIRMABLE(HttpStatus.CONFLICT, "PAYMENT409", "이미 처리 중이거나 승인할 수 없는 결제입니다."),
	PG_RESPONSE_MISMATCH(HttpStatus.BAD_GATEWAY, "PAYMENT502_1", "결제사 응답이 주문 정보와 일치하지 않습니다."),
	PG_RESULT_UNKNOWN(HttpStatus.BAD_GATEWAY, "PAYMENT502", "결제 결과를 확인하지 못했습니다. 잠시 후 결제 내역을 확인해주세요."),
	PAYMENT_COMPENSATED(HttpStatus.CONFLICT, "PAYMENT409_1", "결제 승인 후 주문을 확정하지 못해 결제가 자동 취소되었습니다."),
	PAYMENT_NOT_RECONCILABLE(HttpStatus.CONFLICT, "PAYMENT409_2", "결과 확인이 필요한 결제가 아닙니다.");

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
