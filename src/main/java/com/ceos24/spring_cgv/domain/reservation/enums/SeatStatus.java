package com.ceos24.spring_cgv.domain.reservation.enums;

public enum SeatStatus {

    RESERVED,   // 결제가 진행 중이거나 결제가 완료되어 점유된 좌석
    UNRESERVED  // 점유가 풀려 다시 판매할 수 있는 좌석
}
