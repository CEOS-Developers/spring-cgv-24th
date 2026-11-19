package com.ceos24.spring_cgv.domain.payment.metrics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 좌석 선점 시도의 결과. 메트릭 태그 값으로 쓰이므로 종류를 enum으로 고정한다.
@Getter
@RequiredArgsConstructor
public enum SeatHoldResult {

    SUCCESS("success"),                     // 선점 성공
    LOCK_TIMEOUT("lock_timeout"),           // 같은 좌석의 동시 요청으로 락 대기 시간 초과 (트래픽 문제)
    ALREADY_HELD("already_held"),           // 다른 고객이 결제창에서 진행 중 (정상 비즈니스 상황)
    STORE_UNAVAILABLE("store_unavailable"); // Redis 장애 또는 락 대기 중 인터럽트

    private final String tag;
}
