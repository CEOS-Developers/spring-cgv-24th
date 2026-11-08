package com.ceos24.spring_cgv.domain.payment.enums;

public enum PaymentStatus {

    READY,                  // 결제 요청 정보가 등록된 상태
    IN_PROGRESS,            // 사용자가 결제 수단을 선택하고 인증을 완료한 상태
    WAITING_FOR_DEPOSIT,    // 가상계좌 결제에서 입금을 기다리는 상태
    DONE,                   // 결제가 승인된 상태
    CANCELED,               // 승인된 결제가 전액 취소된 상태
    PARTIAL_CANCELED,       // 승인된 결제가 부분 취소된 상태
    ABORTED,                // 결제 승인이 실패한 상태
    EXPIRED                 // 결제 유효 시간이 지나 승인되지 못한 상태
}