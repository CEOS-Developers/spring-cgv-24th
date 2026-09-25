package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.enums.AgeRating;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.domain.reservation.dto.ReservationSnapshot;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.entity.ReservedSeat;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.repository.ReservedSeatRepository;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ScreeningRepository screeningRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservedSeatRepository reservedSeatRepository;
    @Mock
    private ScreeningSeatRepository screeningSeatRepository;
    @Mock
    private ScreeningSeatLockService screeningSeatLockService;
    @InjectMocks
    private ReservationService reservationService;

    @Test
    void 이미_예약된_좌석은_다시_예매할_수_없다() {
        User user = mock(User.class);
        Screening screening = mock(Screening.class);
        Screen screen = mock(Screen.class);
        when(screening.getScreen()).thenReturn(screen);
        when(screening.getId()).thenReturn(8L);
        when(screen.getRowCount()).thenReturn(10);
        when(screen.getSeatsPerRow()).thenReturn(12);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(screeningRepository.findMovieIdById(8L)).willReturn(Optional.of(4L));
        given(movieRepository.findByIdForShare(4L)).willReturn(Optional.of(
                new Movie("영화", "설명", 120, LocalDate.of(2026, 9, 1), AgeRating.ALL)));
        given(screeningRepository.findById(8L)).willReturn(Optional.of(screening));
        given(screeningRepository.findByIdWithLock(8L)).willReturn(Optional.of(screening));
        given(reservedSeatRepository.existsReservedByScreeningIdAndCoordinates(
                8L, Set.of(new SeatCoordinate("A", 1)))).willReturn(true);
        ReservationCreateRequest request = new ReservationCreateRequest(
                1L, 8L, List.of(new ReservedSeatRequest("A", 1))
        );

        assertThatThrownBy(() -> reservationService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED);

        then(reservationRepository).shouldHaveNoInteractions();
    }

    @Test
    void 좌석_잠금_전_읽은_예매가_그사이_취소됐으면_다시_취소하지_않는다() {
        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        Screening screening = mock(Screening.class);
        ReservedSeat history = mock(ReservedSeat.class);
        ScreeningSeat lockedSeat = mock(ScreeningSeat.class);
        when(reservationRepository.findWithSeatsById(12L)).thenReturn(Optional.of(reservation));
        when(reservation.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);
        when(reservation.getStatus()).thenReturn(ReservationStatus.RESERVED);
        when(reservation.getReservedSeats()).thenReturn(List.of(history));
        when(history.getScreeningSeat()).thenReturn(lockedSeat);
        when(history.getSeatRow()).thenReturn("A");
        when(history.getSeatNumber()).thenReturn(1);
        when(reservation.getScreening()).thenReturn(screening);
        when(screening.getId()).thenReturn(8L);
        given(screeningSeatLockService.lockSeats(8L, List.of(new SeatCoordinate("A", 1))))
                .willReturn(List.of(lockedSeat));
        given(reservationRepository.findSnapshotById(12L)).willReturn(Optional.of(
                new ReservationSnapshot(1L, 8L, 4L, ReservationStatus.CANCELED, null)));

        assertThatThrownBy(() -> reservationService.cancel(12L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_ALREADY_CANCELED);
        then(lockedSeat).shouldHaveNoInteractions();
    }
}
