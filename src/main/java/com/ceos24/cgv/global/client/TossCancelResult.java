package com.ceos24.cgv.global.client;

// 승인과 같은 세 갈래다. 취소에서 "환불됐는지 모름"을 거절로 처리하면, 회원이 다시 요청할 때 새 멱등키로
// 같은 금액이 한 번 더 환불된다. 부분 취소는 잔액이 남아 있어 토스도 그 두 번째 요청을 거절하지 않는다.
public sealed interface TossCancelResult {

    // cancel은 이번 요청의 취소 거래다. 응답의 cancels 중 마지막 항목이다.
    record Canceled(TossPayment payment, TossPayment.Cancel cancel) implements TossCancelResult {
    }

    record Rejected(String code, String message) implements TossCancelResult {
    }

    record Unknown(String reason) implements TossCancelResult {
    }
}
