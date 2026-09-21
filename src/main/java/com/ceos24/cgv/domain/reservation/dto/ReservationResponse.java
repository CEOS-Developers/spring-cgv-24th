package com.ceos24.cgv.domain.reservation.dto;

import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.screening.entity.Screening;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record ReservationResponse(
        Long id,
        Long userId,
        ScreeningSummary screening,
        ReservationStatus status,
        String statusName,
        List<SeatSummary> seats,
        int totalPrice,
        LocalDateTime selectedAt,
        LocalDateTime expiresAt,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt
) {
    public record ScreeningSummary(
            Long id,
            String movieTitle,
            String theaterName,
            String branchName,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        public static ScreeningSummary from(Screening s) {
            return new ScreeningSummary(
                    s.getId(),
                    s.getMovie().getTitle(),
                    s.getTheater().getName(),
                    s.getTheater().getBranch().getName(),
                    s.getStartAt(),
                    s.getEndAt()
            );
        }
    }

    public record SeatSummary(
            String label,
            AudienceType audienceType,
            String audienceTypeName,
            int paidPrice
    ) {
        public static SeatSummary from(ReservationSeat seat) {
            return new SeatSummary(
                    seat.getLabel(),
                    seat.getAudienceType(),
                    seat.getAudienceType().getDisplayName(),
                    seat.getPaidPrice()
            );
        }
    }

    // 만료된 선점은 DB 상태가 아직 PENDING이어도 이미 좌석을 놓은 것이나 마찬가지다.
    // 정리는 다음 좌석 선점 요청이 하고, 조회는 현재 사실만 보여준다.
    public static ReservationResponse from(Reservation r, LocalDateTime now) {
        List<SeatSummary> seats = r.getSeats().stream()
                .sorted(Comparator.comparingInt(ReservationSeat::getRowNum)
                        .thenComparingInt(ReservationSeat::getColNum))
                .map(SeatSummary::from)
                .toList();

        ReservationStatus status = r.isExpired(now) ? ReservationStatus.EXPIRED : r.getStatus();

        return new ReservationResponse(
                r.getId(),
                r.getUser().getId(),
                ScreeningSummary.from(r.getScreening()),
                status,
                status.getDisplayName(),
                seats,
                r.getTotalPrice(),
                r.getCreatedAt(),
                r.getExpiresAt(),
                r.getConfirmedAt(),
                r.getCancelledAt()
        );
    }
}
