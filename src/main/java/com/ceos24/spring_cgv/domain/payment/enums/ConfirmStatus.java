package com.ceos24.spring_cgv.domain.payment.enums;

public enum ConfirmStatus {

    REQUESTED,  // 토스에 승인을 보냈으나 결과를 확인하지 못한 상태
    APPROVED,   // 토스가 승인한 상태. 결제가 READY로 남아 있다면 복구가 필요하다.
    FAILED      // 토스가 명시적으로 거절한 상태
}
