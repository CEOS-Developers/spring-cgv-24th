package com.ceos24.springboot.reservation.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ReservationStatus {
    Pending, // 좌석 임시 점유
    Reserved,
    Canceled,
    Expired // 대기 시간 만료
}
