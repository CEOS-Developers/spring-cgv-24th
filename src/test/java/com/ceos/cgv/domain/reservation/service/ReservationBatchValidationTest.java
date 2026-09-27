package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.cinema.enums.ScreenType;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.enums.AgeRating;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.dto.*;
import com.ceos.cgv.domain.reservation.repository.*;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationBatchValidationTest {
    @Mock Clock clock;
    @Mock UserRepository users;
    @Mock MovieRepository movies;
    @Mock ScreeningRepository screenings;
    @Mock ReservationRepository reservations;
    @Mock ReservedSeatRepository seats;
    @Mock ScreeningSeatRepository screeningSeats;
    @InjectMocks ReservationCreationService service;
    private Screening screening;

    @BeforeEach
    void base() {
        Movie movie = new Movie("영화", "설명", 120, LocalDate.of(2026,9,1), AgeRating.ALL);
        Screen screen = new Screen(null, ScreenType.GENERAL, 10, 12);
        screening = new Screening(movie, screen, LocalDateTime.of(2026,9,20,12,30));
        ReflectionTestUtils.setField(screening, "id", 8L);
        when(users.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(screenings.findMovieIdById(8L)).thenReturn(Optional.of(4L));
        when(movies.findByIdForShare(4L)).thenReturn(Optional.of(movie));
        when(screenings.findById(8L)).thenReturn(Optional.of(screening));
    }

    @Test
    void 여러_좌석을_한번_검사하고_모두_저장한다() {
        Set<SeatCoordinate> requested = Set.of(new SeatCoordinate("A",1), new SeatCoordinate("B",2));
        when(seats.existsReservedByScreeningIdAndCoordinates(8L, requested)).thenReturn(false);
        when(screenings.findByIdWithLock(8L)).thenReturn(Optional.of(screening));
        when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.create(new ReservationCreateRequest(1L,8L,
                List.of(new ReservedSeatRequest("A",1),new ReservedSeatRequest("B",2))));
        assertThat(result.seats()).hasSize(2);
        verify(seats).existsReservedByScreeningIdAndCoordinates(8L, requested);
        verifyNoMoreInteractions(seats);
    }

    @Test
    void 요청_내_중복이면_좌석_조회_전에_거절한다() {
        assertThatThrownBy(() -> service.create(new ReservationCreateRequest(1L,8L,
                List.of(new ReservedSeatRequest("A",1),new ReservedSeatRequest("A",1)))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException)e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
        verifyNoInteractions(seats, reservations);
    }

    @Test
    void 범위_밖_좌석이면_좌석_조회_전에_거절한다() {
        assertThatThrownBy(() -> service.create(new ReservationCreateRequest(1L,8L,
                List.of(new ReservedSeatRequest("A",99)))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException)e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SEAT);
        verifyNoInteractions(seats, reservations);
    }

    @Test
    void 내부_호출에서도_null과_잘못된_좌표를_업무_오류로_거절한다() {
        List<ReservedSeatRequest> invalid = Arrays.asList(null,
                new ReservedSeatRequest(null, 1), new ReservedSeatRequest("", 1),
                new ReservedSeatRequest("AA", 1), new ReservedSeatRequest("a", 1),
                new ReservedSeatRequest("A", null), new ReservedSeatRequest("A", 0));
        for (ReservedSeatRequest seat : invalid) {
            assertThatThrownBy(() -> service.create(new ReservationCreateRequest(1L, 8L,
                    Collections.singletonList(seat))))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_SEAT);
        }
        verifyNoInteractions(seats, reservations);
    }
}
