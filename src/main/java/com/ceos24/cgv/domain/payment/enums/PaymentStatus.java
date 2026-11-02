package com.ceos24.cgv.domain.payment.enums;

// 결제 상태
//응답이 오지 않았더라도 외부에서는 승인이 성공했을 수 있음 ( Failed , Unknown 을 구분을 잘하기 ) => 타임아웃으로 실패를 확정하면 안됨
public enum PaymentStatus {
    READY,               // 결제 준비
    APPROVING,           // 승인 요청 처리 중
    DONE,                // 승인 완료
    CANCELING,           // 취소 요청 처리 중
    PARTIALLY_CANCELED,   // 일부 금액 취소 완료
    CANCELED,            // 전체 금액 취소 완료
    FAILED,              // 승인 실패가 명확히 확인됨
    UNKNOWN              // 통신 오류 등으로 처리 결과 확인 필요
}