package com.ceos24.spring_cgv.domain.payment.enums;

public enum CancelStatus {

    REQUESTED,  // 토스에 취소를 보냈으나 결과를 확인하지 못한 상태
    DONE,       // 취소가 확정된 상태
    FAILED      // 토스가 명시적으로 거절한 상태
}
