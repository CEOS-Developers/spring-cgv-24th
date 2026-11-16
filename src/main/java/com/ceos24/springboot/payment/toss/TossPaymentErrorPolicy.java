package com.ceos24.springboot.payment.toss;

import org.springframework.web.client.RestClientResponseException;

public final class TossPaymentErrorPolicy {
    private TossPaymentErrorPolicy() {}

    // 승인/취소 명령의 응답에만 적용한다. 조회 실패는 명령 거절의 근거가 아니다.
    public static boolean isDefinitiveRejection(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return status >= 400 && status < 500 && status != 408 && status != 409 && status != 429;
    }
}
