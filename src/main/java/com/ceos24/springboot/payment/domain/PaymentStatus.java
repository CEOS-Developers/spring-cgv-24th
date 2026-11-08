package com.ceos24.springboot.payment.domain;

// 우리 서비스가 관리하는 상태
public enum PaymentStatus {
    READY,       // 결제 대기
    PROCESSING,  // 승인 처리 중
    DONE,        // 승인 완료
    PARTIAL_CANCELED, // 일부 금액 환불 완료
    FAILED,      // 실패가 확정됨
    UNKNOWN,     // 통신 문제 등으로 승인 결과를 확인해야 함
    // (승인 요청 후 응답을 받지 못했다고 해서 토스에서 결제가 실패했다고 단정할 수는 없음. 토스에서는 성공했는데 응답만 유실됐을 수도 있기 때문)
    CANCELED,    // 결제 대기 취소 또는 승인 금액 전액 환불 (approvedAt으로 구분)
    EXPIRED      // 결제 대기 기한 만료
}
