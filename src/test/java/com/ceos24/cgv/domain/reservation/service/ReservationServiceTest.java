package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.cinema.entity.Auditorium;
import com.ceos24.cgv.domain.cinema.entity.AuditoriumType;
import com.ceos24.cgv.domain.cinema.entity.AuditoriumTypeCategory;
import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.entity.Seat;
import com.ceos24.cgv.domain.cinema.repository.SeatRepository;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationSeatRepository reservationSeatRepository;

    @Mock
    private ScreeningRepository screeningRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SeatRepository seatRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void 예매하면_확정상태의_예매와_선택좌석을_저장한다() {
        User user = user(1L);
        Auditorium auditorium = auditorium(10L, "1관");
        Screening screening = screening(20L, auditorium);
        Seat firstSeat = seat(100L, auditorium, 1, 1);
        Seat secondSeat = seat(101L, auditorium, 1, 2);
        ReservationCreateRequest request = new ReservationCreateRequest(
                screening.getId(),
                List.of(101L, 100L)
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(screeningRepository.findById(20L)).thenReturn(Optional.of(screening));
        when(seatRepository.findAllByIdInForUpdate(List.of(100L, 101L)))
                .thenReturn(List.of(firstSeat, secondSeat));
        when(reservationSeatRepository.findSeatIdsByScreeningIdAndStatus(
                20L,
                ReservationStatus.CONFIRMED
        )).thenReturn(List.of());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            ReflectionTestUtils.setField(reservation, "id", 30L);
            return reservation;
        });

        Long result = reservationService.createReservation(1L, request);

        assertEquals(30L, result);

        ArgumentCaptor<Reservation> reservationCaptor =
                ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        assertEquals(ReservationStatus.CONFIRMED, reservationCaptor.getValue().getStatus());
        assertEquals(user, reservationCaptor.getValue().getUser());
        assertEquals(screening, reservationCaptor.getValue().getScreening());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReservationSeat>> seatsCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservationSeatRepository).saveAll(seatsCaptor.capture());
        assertEquals(2, seatsCaptor.getValue().size());
    }

    @Test
    void 중복된_좌석ID가_있으면_예매하지_않는다() {
        User user = user(1L);
        Screening screening = screening(20L, auditorium(10L, "1관"));
        ReservationCreateRequest request = new ReservationCreateRequest(
                20L,
                List.of(100L, 100L)
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(screeningRepository.findById(20L)).thenReturn(Optional.of(screening));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> reservationService.createReservation(1L, request)
        );

        assertEquals(ErrorCode.DUPLICATE_SEAT_REQUEST, exception.getErrorCode());
        verify(seatRepository, never()).findAllByIdInForUpdate(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 다른_상영관의_좌석이면_예매하지_않는다() {
        User user = user(1L);
        Auditorium screeningAuditorium = auditorium(10L, "1관");
        Auditorium otherAuditorium = auditorium(11L, "2관");
        Screening screening = screening(20L, screeningAuditorium);
        Seat otherSeat = seat(100L, otherAuditorium, 1, 1);
        ReservationCreateRequest request = new ReservationCreateRequest(20L, List.of(100L));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(screeningRepository.findById(20L)).thenReturn(Optional.of(screening));
        when(seatRepository.findAllByIdInForUpdate(List.of(100L)))
                .thenReturn(List.of(otherSeat));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> reservationService.createReservation(1L, request)
        );

        assertEquals(ErrorCode.SEAT_NOT_IN_SCREENING_AUDITORIUM, exception.getErrorCode());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 이미_확정된_좌석이면_예매하지_않는다() {
        User user = user(1L);
        Auditorium auditorium = auditorium(10L, "1관");
        Screening screening = screening(20L, auditorium);
        Seat seat = seat(100L, auditorium, 1, 1);
        ReservationCreateRequest request = new ReservationCreateRequest(20L, List.of(100L));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(screeningRepository.findById(20L)).thenReturn(Optional.of(screening));
        when(seatRepository.findAllByIdInForUpdate(List.of(100L))).thenReturn(List.of(seat));
        when(reservationSeatRepository.findSeatIdsByScreeningIdAndStatus(
                20L,
                ReservationStatus.CONFIRMED
        )).thenReturn(List.of(100L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> reservationService.createReservation(1L, request)
        );

        assertEquals(ErrorCode.SEAT_ALREADY_RESERVED, exception.getErrorCode());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 예매를_취소하면_취소상태와_취소시간이_기록된다() {
        User user = user(1L);
        Reservation reservation = Reservation.create(
                screening(20L, auditorium(10L, "1관")),
                user
        );
        ReflectionTestUtils.setField(reservation, "id", 30L);
        when(reservationRepository.findById(30L)).thenReturn(Optional.of(reservation));

        reservationService.cancelReservation(1L, 30L);

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertNotNull(reservation.getCancelledAt());
    }

    private User user(Long id) {
        User user = User.create("테스트 사용자");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Auditorium auditorium(Long id, String name) {
        Cinema cinema = Cinema.create("강남", "서울시 강남구", "서울");
        ReflectionTestUtils.setField(cinema, "id", 1L);
        AuditoriumType type = AuditoriumType.create(
                "일반관",
                AuditoriumTypeCategory.NORMAL,
                10,
                10
        );
        ReflectionTestUtils.setField(type, "id", 1L);
        Auditorium auditorium = Auditorium.create(cinema, type, name);
        ReflectionTestUtils.setField(auditorium, "id", id);
        return auditorium;
    }

    private Screening screening(Long id, Auditorium auditorium) {
        Movie movie = Movie.create("영화 A", LocalDate.now());
        ReflectionTestUtils.setField(movie, "id", 1L);
        Screening screening = Screening.create(
                auditorium,
                movie,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                15_000
        );
        ReflectionTestUtils.setField(screening, "id", id);
        return screening;
    }

    private Seat seat(
            Long id,
            Auditorium auditorium,
            int rowNumber,
            int columnNumber
    ) {
        Seat seat = Seat.create(auditorium, rowNumber, columnNumber);
        ReflectionTestUtils.setField(seat, "id", id);
        return seat;
    }
}
