package com.ceos24.cgv.domain.reservation.entity;

// 좌석이 풀리는 경로가 사용자 취소와 시간 만료로 나뉜다.
// 둘을 CANCELLED 하나로 합치면 나중에 "이 좌석이 왜 풀렸나"를 되짚을 수 없다.
public enum ReservationStatus {

    PENDING("결제대기"),
    RESERVED("예매완료"),
    CANCELLED("취소"),
    EXPIRED("선점만료");

    private final String displayName;

    ReservationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
