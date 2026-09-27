package com.ceos.cgv.domain.reservation.service.hold;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.config.SeatHoldProperties;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.repository.ReservedSeatRepository;
import com.ceos.cgv.domain.reservation.service.seat.ScreeningSeatLockService;
import com.ceos.cgv.domain.reservation.value.SeatCoordinate;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeatHoldCreationTimeTest {
    @Test
    void 회원_잠금_대기_후_좌석을_확보한_시각부터_5분을_적용한다() {
        Instant beforeWait = Instant.parse("2026-09-25T09:00:00Z");
        AtomicReference<Instant> now = new AtomicReference<>(beforeWait);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());

        UserRepository users = mock(UserRepository.class);
        ReservationRepository reservations = mock(ReservationRepository.class);
        ScreeningRepository screenings = mock(ScreeningRepository.class);
        ScreeningSeatRepository seats = mock(ScreeningSeatRepository.class);
        MovieRepository movies = mock(MovieRepository.class);
        ReservedSeatRepository histories = mock(ReservedSeatRepository.class);
        ScreeningSeatLockService locks = mock(ScreeningSeatLockService.class);
        SeatHoldCreationService service = new SeatHoldCreationService(
                users, reservations, screenings, seats, movies, histories, locks,
                new SeatHoldProperties(Duration.ofMinutes(5), 8, 1, 100), clock);

        User user = mock(User.class);
        when(users.findByIdForUpdate(1L)).thenAnswer(invocation -> {
            now.set(beforeWait.plus(Duration.ofMinutes(2)));
            return Optional.of(user);
        });
        Screening screening = mock(Screening.class);
        Screen screen = mock(Screen.class);
        Movie movie = mock(Movie.class);
        ScreeningSeat seat = mock(ScreeningSeat.class);
        when(screenings.findById(8L)).thenReturn(Optional.of(screening));
        when(screening.getId()).thenReturn(8L);
        when(screening.getMovie()).thenReturn(movie);
        when(screening.getScreen()).thenReturn(screen);
        when(movie.getId()).thenReturn(9L);
        when(movies.findByIdForShare(9L)).thenReturn(Optional.of(movie));
        when(screen.getRowCount()).thenReturn(1);
        when(screen.getSeatsPerRow()).thenReturn(1);
        when(seats.countByScreening_Id(8L)).thenReturn(1L);
        when(locks.lockSeats(8L, java.util.Set.of(new SeatCoordinate("A", 1))))
                .thenReturn(List.of(seat));
        when(seat.getScreening()).thenReturn(screening);
        when(seat.getSeatRow()).thenReturn("A");
        when(seat.getSeatNumber()).thenReturn(1);
        when(reservations.saveAndFlush(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(1L,
                UUID.fromString("123e4567-e89b-12d3-a456-426614174030"),
                new SeatHoldCreateRequest(8L, List.of(new ReservedSeatRequest("A", 1))));

        assertThat(result.response().expiresAt())
                .isEqualTo(beforeWait.plus(Duration.ofMinutes(7)));
    }
}
