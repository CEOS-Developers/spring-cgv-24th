package com.ceos.cgv.domain.movie.dto;

import com.ceos.cgv.domain.reservation.enums.ReservationStatus;

import java.time.Instant;

/**
 * 좌석 한 칸과 그 좌석을 점유한 예약의 상태만 담는 내부 조회 결과이며 API 응답이 아니다.
 * 점유한 예약이 없으면 reservationStatus와 reservationExpiresAt은 null이다.
 */
public record SeatOccupancy(String seatRow, Integer seatNumber,
                            ReservationStatus reservationStatus, Instant reservationExpiresAt) {
}
