package com.ceos24.cgv.global.client;

// 조회 결과도 셋으로 나눈다. "토스에 그 결제가 없다"와 "토스에 물어보지 못했다"를 합치면,
// 결과 불명 결제를 정리할 때 조회 실패를 "결제 없음"으로 읽어 승인된 결제를 실패로 확정하게 된다.
public sealed interface TossLookupResult {

    record Found(TossPayment payment) implements TossLookupResult {
    }

    // 토스가 404로 답했다. 인증을 마치지 않았거나 승인 요청이 토스에 닿지 않은 주문이다.
    record NotFound(String code, String message) implements TossLookupResult {
    }

    // 5xx·타임아웃·인증 오류 등. 토스 쪽 상태에 대해 아무것도 알 수 없다.
    record Unavailable(String reason) implements TossLookupResult {
    }
}
