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

        public static SeatSummary from(ReservationDetailRow row) {
            return new SeatSummary(
                    ReservationSeat.label(row.rowNum(), row.colNum()),
                    row.audienceType(),
                    row.audienceType().getDisplayName(),
                    row.paidPrice()
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

        ReservationStatus status = resolveStatus(r.getStatus(), r.getExpiresAt(), now);

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

    // 조회 경로는 엔티티 없이 스칼라 행만 받는다. 헤더 값은 행마다 같으므로 첫 행에서 읽고,
    // 좌석 정렬은 쿼리의 ORDER BY가 이미 끝냈다.
    public static ReservationResponse of(List<ReservationDetailRow> rows, LocalDateTime now) {
        ReservationDetailRow head = rows.getFirst();

        List<SeatSummary> seats = rows.stream()
                .map(SeatSummary::from)
                .toList();
        int totalPrice = rows.stream()
                .mapToInt(ReservationDetailRow::paidPrice)
                .sum();

        ReservationStatus status = resolveStatus(head.status(), head.expiresAt(), now);

        return new ReservationResponse(
                head.reservationId(),
                head.userId(),
                new ScreeningSummary(
                        head.screeningId(),
                        head.movieTitle(),
                        head.theaterName(),
                        head.branchName(),
                        head.startAt(),
                        head.endAt()
                ),
                status,
                status.getDisplayName(),
                seats,
                totalPrice,
                head.selectedAt(),
                head.expiresAt(),
                head.confirmedAt(),
                head.cancelledAt()
        );
    }

    // 프로젝션 경로에는 엔티티가 없어 Reservation.isExpired()를 부를 수 없다.
    private static ReservationStatus resolveStatus(ReservationStatus status,
                                                   LocalDateTime expiresAt,
                                                   LocalDateTime now) {
        boolean expired = status == ReservationStatus.PENDING && !now.isBefore(expiresAt);
        return expired ? ReservationStatus.EXPIRED : status;
    }
}
