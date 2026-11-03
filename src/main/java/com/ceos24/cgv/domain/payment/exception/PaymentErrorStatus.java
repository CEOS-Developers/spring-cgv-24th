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
	PG_RESULT_UNKNOWN(HttpStatus.BAD_GATEWAY, "PAYMENT502", "결제 결과를 확인하지 못했습니다. 잠시 후 결제 내역을 확인해주세요.");

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
