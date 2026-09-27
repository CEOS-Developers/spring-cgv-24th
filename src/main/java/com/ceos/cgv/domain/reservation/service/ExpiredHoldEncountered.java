package com.ceos.cgv.domain.reservation.service;

// 현재 시도를 롤백한 뒤 조정 서비스가 만료 정리와 재시도를 수행하도록 알린다.
final class ExpiredHoldEncountered extends RuntimeException {
    private final Long reservationId;
    private final boolean sameRequestKey;

    ExpiredHoldEncountered(Long reservationId, boolean sameRequestKey) {
        this.reservationId = reservationId;
        this.sameRequestKey = sameRequestKey;
    }

    Long reservationId() {
        return reservationId;
    }

    boolean sameRequestKey() {
        return sameRequestKey;
    }
}
