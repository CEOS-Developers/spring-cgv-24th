package com.ceos24.cgv.domain.reservation.dto.response;

import com.ceos24.cgv.domain.cinema.entity.Auditorium;
import com.ceos24.cgv.domain.movie.entity.Movie;
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
        int totalPrice,
        List<ReservationSeatResponse> seats,
        LocalDateTime reservedAt,
        LocalDateTime cancelledAt
) {

    public static ReservationResponse from(
            Reservation reservation,
            List<ReservationSeat> reservationSeats
    ) {
        Screening screening = reservation.getScreening();
        Movie movie = screening.getMovie();
        Auditorium auditorium = screening.getAuditorium();

        List<ReservationSeatResponse> seats = reservationSeats.stream()
                .map(ReservationSeatResponse::from)
                .toList();

        return new ReservationResponse(
                reservation.getId(),
                reservation.getUser().getId(),
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
                screening.getPrice() * seats.size(),
                seats,
                reservation.getReservedAt(),
                reservation.getCancelledAt()
        );
    }
}
