package com.ceos24.cgv.domain.reservation.dto;

import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.screening.entity.Screening;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public record ReservationResponse(
        Long id,
        Long userId,
        ScreeningSummary screening,
        ReservationStatus status,
        String statusName,
        List<SeatSummary> seats,
        // 처음 잡은 좌석 전부의 금액이다. 취소해도 줄지 않아, 무엇을 얼마에 샀었는지가 남는다.
        int totalPrice,
        // 취소되지 않은 좌석의 금액이다. 부분 취소 뒤에 남은 예매의 값이다.
        int remainingPrice,
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
            int paidPrice,
            // 회원이 취소한 좌석이다. 선점 만료로 풀린 좌석은 false이고, 예매 상태(EXPIRED)가 그 사실을 말한다.
            boolean cancelled
    ) {
        public static SeatSummary from(ReservationSeat seat) {
            return new SeatSummary(
                    seat.getLabel(),
                    seat.getAudienceType(),
                    seat.getAudienceType().getDisplayName(),
                    seat.getPaidPrice(),
                    seat.getCancelledAt() != null
            );
        }

        public static SeatSummary from(ReservationDetailRow row) {
            return new SeatSummary(
                    ReservationSeat.label(row.rowNum(), row.colNum()),
                    row.audienceType(),
                    row.audienceType().getDisplayName(),
                    row.paidPrice(),
                    row.seatCancelledAt() != null
            );
        }
    }

    public static ReservationResponse from(Reservation r, LocalDateTime now) {
        List<SeatSummary> seats = r.getSeats().stream()
                .sorted(Comparator.comparingInt(ReservationSeat::getRowNum)
                        .thenComparingInt(ReservationSeat::getColNum))
                .map(SeatSummary::from)
                .toList();

        ReservationStatus status = r.getStatus().resolve(r.getExpiresAt(), now);

        return new ReservationResponse(
                r.getId(),
                r.getUser().getId(),
                ScreeningSummary.from(r.getScreening()),
                status,
                status.getDisplayName(),
                seats,
                r.getTotalPrice(),
                remainingPriceOf(seats),
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

        ReservationStatus status = head.status().resolve(head.expiresAt(), now);

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
                remainingPriceOf(seats),
                head.selectedAt(),
                head.expiresAt(),
                head.confirmedAt(),
                head.cancelledAt()
        );
    }

    private static int remainingPriceOf(List<SeatSummary> seats) {
        return seats.stream()
                .filter(seat -> !seat.cancelled())
                .mapToInt(SeatSummary::paidPrice)
                .sum();
    }

    // 행은 예매 id 내림차순으로 온다. LinkedHashMap이라 묶은 뒤에도 그 순서가 유지된다.
    public static List<ReservationResponse> listOf(List<ReservationDetailRow> rows, LocalDateTime now) {
        return rows.stream()
                .collect(Collectors.groupingBy(ReservationDetailRow::reservationId, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(group -> of(group, now))
                .toList();
    }
}
