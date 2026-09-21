package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.exception.ReservationException;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.ScreenType;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationSeatRepository reservationSeatRepository;

    @Mock
    private ScreeningRepository screeningRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    @DisplayName("상영 일정의 좌석을 예매한다")
    void createReservation_success() {
        // given
        User user = mock(User.class);
        Screening screening = mock(Screening.class);
        Screen screen = mock(Screen.class);
        ScreenType screenType = mock(ScreenType.class);
        Seat seat1 = mock(Seat.class);
        Seat seat2 = mock(Seat.class);

        given(user.getId()).willReturn(1L);
        given(screening.getId()).willReturn(100L);
        given(screening.getStartTime())
                .willReturn(LocalDateTime.now().plusDays(1));
        given(screening.getScreen()).willReturn(screen);

        given(screen.getId()).willReturn(200L);
        given(screen.getScreenType()).willReturn(screenType);
        given(screenType.getBasePrice()).willReturn(15000);

        given(seat1.getId()).willReturn(10L);
        given(seat1.getSeatRow()).willReturn("A");
        given(seat1.getSeatColumn()).willReturn(1);
        given(seat1.getScreen()).willReturn(screen);

        given(seat2.getId()).willReturn(11L);
        given(seat2.getSeatRow()).willReturn("A");
        given(seat2.getSeatColumn()).willReturn(2);
        given(seat2.getScreen()).willReturn(screen);

        ReservationCreateRequest request =
                new ReservationCreateRequest(
                        100L,
                        List.of(10L, 11L)
                );

        given(userRepository.findById(1L))
                .willReturn(Optional.of(user));
        given(screeningRepository.findById(100L))
                .willReturn(Optional.of(screening));
        given(seatRepository.findAllByIdForUpdate(
                List.of(10L, 11L)
        )).willReturn(List.of(seat1, seat2));
        given(reservationSeatRepository.findReservedSeatIds(
                100L,
                List.of(10L, 11L),
                ReservationStatus.RESERVED
        )).willReturn(List.of());

        given(reservationRepository.save(any(Reservation.class)))
                .willAnswer(invocation ->
                        invocation.getArgument(0)
                );

        // when
        ReservationResponse response =
                reservationService.createReservation(
                        1L,
                        request
                );

        // then
        assertThat(response.totalPrice()).isEqualTo(30000);
        assertThat(response.status())
                .isEqualTo(ReservationStatus.RESERVED);
        assertThat(response.seats()).hasSize(2);

        then(reservationRepository)
                .should()
                .save(any(Reservation.class));
    }

    @Test
    @DisplayName("이미 예매된 좌석이 포함되면 예외가 발생한다")
    void createReservation_alreadyReserved() {
        // given
        User user = mock(User.class);
        Screening screening = mock(Screening.class);
        Screen screen = mock(Screen.class);
        Seat seat = mock(Seat.class);

        given(screening.getId()).willReturn(100L);
        given(screening.getStartTime())
                .willReturn(LocalDateTime.now().plusDays(1));
        given(screening.getScreen()).willReturn(screen);
        given(screen.getId()).willReturn(200L);
        given(seat.getScreen()).willReturn(screen);

        ReservationCreateRequest request =
                new ReservationCreateRequest(
                        100L,
                        List.of(10L)
                );

        given(userRepository.findById(1L))
                .willReturn(Optional.of(user));
        given(screeningRepository.findById(100L))
                .willReturn(Optional.of(screening));
        given(seatRepository.findAllByIdForUpdate(
                List.of(10L)
        )).willReturn(List.of(seat));
        given(reservationSeatRepository.findReservedSeatIds(
                100L,
                List.of(10L),
                ReservationStatus.RESERVED
        )).willReturn(List.of(10L));

        // when & then
        assertThatThrownBy(() ->
                reservationService.createReservation(
                        1L,
                        request
                )
        ).isInstanceOf(ReservationException.class);

        then(reservationRepository)
                .should(never())
                .save(any(Reservation.class));
    }

    @Test
    @DisplayName("본인의 예매를 취소한다")
    void cancelReservation_success() {
        // given
        User user = mock(User.class);
        Screening screening = mock(Screening.class);

        given(user.getId()).willReturn(1L);

        Reservation reservation =
                Reservation.create(user, screening);

        given(userRepository.findById(1L))
                .willReturn(Optional.of(user));
        given(reservationRepository.findByIdForUpdate(10L))
                .willReturn(Optional.of(reservation));

        // when
        reservationService.cancelReservation(1L, 10L);

        // then
        assertThat(reservation.getStatus())
                .isEqualTo(ReservationStatus.CANCELED);
    }
}