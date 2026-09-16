package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.exception.ReservationErrorStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.schedule.repository.ScheduleRepository;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationSeatRepository reservationSeatRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void 상영_시작_전이면_여러_좌석을_예매한다() {
        var user = user(1L);
        var theater = theater(1L);
        var movie = movie(1L);
        var screen = screen(1L, theater);
        var schedule = schedule(1L, movie, screen, LocalDateTime.now().plusHours(1));
        var firstSeat = seat(10L, screen, 1, 1);
        var secondSeat = seat(11L, screen, 1, 2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(seatRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(firstSeat, secondSeat));
        when(reservationSeatRepository.findByScheduleIdAndSeatIdIn(1L, List.of(10L, 11L))).thenReturn(List.of());
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 100L));
        when(reservationSeatRepository.save(any(ReservationSeat.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 200L));

        Long result = reservationService.create(1L, new ReservationCreateRequest(1L, List.of(10L, 11L)));

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        verify(reservationSeatRepository, times(2)).save(any(ReservationSeat.class));
        assertThat(result).isEqualTo(100L);
        assertThat(captor.getValue().getTotalPrice()).isEqualTo(30_000);
        assertThat(captor.getValue().getSeatSummary()).isEqualTo("A1, A2");
    }

    @Test
    void 중복_좌석을_요청하면_조회없이_예외가_발생한다() {
        assertThatThrownBy(() -> reservationService.create(1L, new ReservationCreateRequest(1L, List.of(10L, 10L))))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.DUPLICATE_SEAT_REQUEST);
        verifyNoInteractions(userRepository, scheduleRepository, seatRepository, reservationRepository, reservationSeatRepository);
    }

    @Test
    void 이미_상영이_시작된_회차는_예매할_수_없다() {
        var theater = theater(1L);
        var screen = screen(1L, theater);
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now().minusMinutes(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> reservationService.create(1L, new ReservationCreateRequest(1L, List.of(10L))))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.RESERVATION_CLOSED);
        verifyNoInteractions(seatRepository, reservationRepository, reservationSeatRepository);
    }

    @Test
    void 본인의_예매_상세를_좌석정보와_함께_조회한다() {
        var user = user(1L);
        var theater = theater(1L);
        var screen = screen(1L, theater);
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now().plusHours(1));
        var reservation = reservation(100L, user, schedule, "A1");
        var seat = seat(10L, screen, 1, 1);
        when(reservationRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(reservation));
        when(reservationSeatRepository.findByReservationIdWithSeat(100L))
                .thenReturn(List.of(reservationSeat(200L, reservation, seat)));

        ReservationResponse result = reservationService.findById(100L, 1L);

        assertThat(result.reservationId()).isEqualTo(100L);
        assertThat(result.seatSummary()).isEqualTo("A1");
        assertThat(result.seats()).hasSize(1);
        assertThat(result.seats().getFirst().seatId()).isEqualTo(10L);
    }

    @Test
    void 취소_가능_마감_전이면_예매를_취소하고_좌석점유를_삭제한다() {
        var user = user(1L);
        var theater = theater(1L);
        var screen = screen(1L, theater);
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now().plusMinutes(30));
        var reservation = reservation(100L, user, schedule, "A1");
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

        reservationService.cancel(100L, 1L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationSeatRepository).deleteByReservationId(100L);
    }

    @Test
    void 취소마감이_지나면_예매를_취소할_수_없다() {
        var user = user(1L);
        var theater = theater(1L);
        var screen = screen(1L, theater);
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now().plusMinutes(10));
        var reservation = reservation(100L, user, schedule, "A1");
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(100L, 1L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.CANCEL_DEADLINE_PASSED);
        verify(reservationSeatRepository, never()).deleteByReservationId(any());
    }
}
