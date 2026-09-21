package com.ceos24.cgv.domain.reservation.dto;

import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;

import java.time.LocalDateTime;

// 예매 단건 조회 결과의 한 행. 좌석 하나당 한 행이라 헤더 값은 행마다 반복된다.
// 반복되는 값이 전부 스칼라라서, 엔티티로 가져올 때처럼 안 쓰는 컬럼이 좌석 수만큼
// 따라오는 일이 없다.
public record ReservationDetailRow(
        Long reservationId,
        Long userId,
        ReservationStatus status,
        LocalDateTime selectedAt,
        LocalDateTime expiresAt,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt,
        Long screeningId,
        String movieTitle,
        String theaterName,
        String branchName,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int rowNum,
        int colNum,
        AudienceType audienceType,
        int paidPrice
) {
}
