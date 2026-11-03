package com.ceos24.cgv.domain.reservation.dto.response;

import com.ceos24.cgv.domain.cinema.entity.Auditorium;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.screening.entity.Screening;

import java.time.LocalDateTime;
import java.util.List;

public record ReservationResponse(
        Long id,
        Long userId,
        Long screeningId,
        Long movieId,
        String movieName,
        Long cinemaId,
        String cinemaName,
        Long auditoriumId,
        String auditoriumName,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        ReservationStatus status,
        long totalPrice,
        List<ReservationSeatResponse> seats,
        LocalDateTime reservedAt,
        LocalDateTime cancelledAt,
        Long paymentId,
        String orderId,
        long totalAmount,
        LocalDateTime expiresAt,
        LocalDateTime confirmedAt
) {

    public static ReservationResponse from(
            Reservation reservation,
            List<ReservationSeat> reservationSeats,
            Payment payment
    ) {
        Screening screening = reservation.getScreening();
        Movie movie = screening.getMovie();
        Auditorium auditorium = screening.getAuditorium();

        List<ReservationSeatResponse> seats = reservationSeats.stream()
                .map(ReservationSeatResponse::from)
                .toList();

        // 결제 도입 전 예매만 현재 회차 가격으로 계산한다. 신규 예매는 확정된 결제 금액을 사용한다.
        long amount = payment == null ? Math.multiplyExact((long) screening.getPrice(), seats.size()) : payment.getTotalAmount();

        return new ReservationResponse(
                reservation.getId(),
                reservation.getUserEntity().getId(),
                screening.getId(),
                movie.getId(),
                movie.getName(),
                auditorium.getCinema().getId(),
                auditorium.getCinema().getName(),
                auditorium.getId(),
                auditorium.getName(),
                screening.getStartsAt(),
                screening.getEndsAt(),
                reservation.getStatus(),
                amount,
                seats,
                reservation.getReservedAt(),
                reservation.getCancelledAt(),
                payment == null ? null : payment.getId(),
                payment == null ? null : payment.getOrderId(),
                amount,
                payment == null ? null : payment.getExpiresAt(),
                reservation.getConfirmedAt()
        );
    }
}
