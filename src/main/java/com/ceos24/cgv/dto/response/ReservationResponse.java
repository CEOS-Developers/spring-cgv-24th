package com.ceos24.cgv.dto.response;

import com.ceos24.cgv.domain.Reservation;
import com.ceos24.cgv.domain.ReservationSeat;
import com.ceos24.cgv.domain.ReservationStatus;
import com.ceos24.cgv.domain.Screening;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record ReservationResponse(
        Long id,
        Long userId,
        ScreeningSummary screening,
        ReservationStatus status,
        List<String> seats,
        int totalPrice,
        LocalDateTime reservedAt,
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

    public static ReservationResponse from(Reservation r) {
        List<String> seatLabels = r.getSeats().stream()
                .sorted(Comparator.comparingInt(ReservationSeat::getRowNum)
                        .thenComparingInt(ReservationSeat::getColNum))
                .map(s -> String.valueOf((char) ('A' + s.getRowNum() - 1)) + s.getColNum())
                .toList();

        return new ReservationResponse(
                r.getId(),
                r.getUser().getId(),
                ScreeningSummary.from(r.getScreening()),
                r.getStatus(),
                seatLabels,
                r.getTotalPrice(),
                r.getReservedAt(),
                r.getCancelledAt()
        );
    }
}
