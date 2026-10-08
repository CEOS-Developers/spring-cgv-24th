package com.ceos24.cgv.global.client;

// 승인 호출의 결과는 셋으로 갈린다. 실패를 하나로 합치면 "돈이 안 빠진 거절"과 "빠졌는지 모르는 실패"를
// 같게 처리하게 되고, 뒤쪽을 거절로 처리하면 돈은 빠졌는데 결제 실패로 기록된다.
public sealed interface TossConfirmResult {

    record Approved(TossPayment payment) implements TossConfirmResult {
    }

    // 토스가 승인하지 않았음이 확실하다. 돈이 빠지지 않았으므로 우리 쪽도 실패로 확정해도 된다.
    record Rejected(String code, String message) implements TossConfirmResult {
    }

    // 타임아웃·5xx처럼 토스 쪽 결과를 알 수 없다. 결제 조회로 실제 상태를 확인하기 전까지 확정하지 않는다.
    record Unknown(String reason) implements TossConfirmResult {
    }
}
