package com.ceos.cgv.domain.reservation.service.hold;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.cinema.enums.ScreenType;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.enums.AgeRating;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.config.SeatHoldProperties;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.repository.ReservedSeatRepository;
import com.ceos.cgv.domain.reservation.service.hold.SeatHoldCreationService;
import com.ceos.cgv.domain.reservation.service.result.HoldCreationResult;
import com.ceos.cgv.domain.reservation.service.seat.ScreeningSeatLockService;
import com.ceos.cgv.domain.reservation.value.SeatCoordinate;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatHoldBatchValidationTest {
    @Mock UserRepository users;
    @Mock MovieRepository movies;
    @Mock ScreeningRepository screenings;
    @Mock ReservationRepository reservations;
    @Mock ReservedSeatRepository histories;
    @Mock ScreeningSeatRepository inventory;
    @Mock ScreeningSeatLockService locks;
    private SeatHoldCreationService service;
    private Screening screening;

    @BeforeEach
    void setUp() {
        service = new SeatHoldCreationService(users, reservations, screenings, inventory, movies, histories,
                locks, new SeatHoldProperties(Duration.ofMinutes(5), 8, 1, 100),
                Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 여러_좌석을_한번_검사하고_모두_선점한다() {
        stubScreening();
        Set<SeatCoordinate> requested = Set.of(new SeatCoordinate("A", 1), new SeatCoordinate("B", 2));
        stubInventory(requested);
        when(reservations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        HoldCreationResult result = create(List.of(new ReservedSeatRequest("A", 1), new ReservedSeatRequest("B", 2)));

        assertThat(result.response().seats()).hasSize(2);
        verify(histories).existsReservedByScreeningIdAndCoordinates(8L, requested);
        verifyNoMoreInteractions(histories);
        verify(locks).lockSeats(8L, requested);
    }

    @Test
    void 요청_내_중복이면_좌석_조회_전에_거절한다() {
        assertError(List.of(new ReservedSeatRequest("A", 1), new ReservedSeatRequest("A", 1)),
                ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
        verifyNoInteractions(users, screenings, inventory, locks, histories, reservations);
    }

    @Test
    void 범위_밖_좌석이면_좌석_조회_전에_거절한다() {
        stubScreening();
        assertError(List.of(new ReservedSeatRequest("A", 99)), ErrorCode.INVALID_SEAT);
        verifyNoInteractions(inventory, locks, histories);
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void 내부_호출에서도_null과_잘못된_좌표를_업무_오류로_거절한다() {
        List<ReservedSeatRequest> invalid = Arrays.asList(null,
                new ReservedSeatRequest(null, 1), new ReservedSeatRequest("", 1),
                new ReservedSeatRequest("AA", 1), new ReservedSeatRequest("a", 1),
                new ReservedSeatRequest("A", null), new ReservedSeatRequest("A", 0));
        for (ReservedSeatRequest seat : invalid) {
            assertError(Collections.singletonList(seat), ErrorCode.INVALID_SEAT);
        }
        verifyNoInteractions(users, screenings, inventory, locks, histories, reservations);
    }

    @Test
    void 기존_확정_이력에_점유된_좌석은_다시_선점하지_않는다() {
        stubScreening();
        Set<SeatCoordinate> requested = Set.of(new SeatCoordinate("A", 1));
        stubInventory(requested);
        when(histories.existsReservedByScreeningIdAndCoordinates(8L, requested)).thenReturn(true);
        assertError(List.of(new ReservedSeatRequest("A", 1)), ErrorCode.SEAT_ALREADY_RESERVED);
        verify(reservations, never()).saveAndFlush(any(Reservation.class));
    }

    private void stubScreening() {
        Movie movie = new Movie("영화", "설명", 120, LocalDate.of(2026, 9, 1), AgeRating.ALL);
        ReflectionTestUtils.setField(movie, "id", 4L);
        Screen screen = new Screen(null, ScreenType.GENERAL, 10, 12);
        screening = new Screening(movie, screen, LocalDateTime.of(2026, 9, 20, 12, 30));
        ReflectionTestUtils.setField(screening, "id", 8L);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(User.class)));
        when(screenings.findById(8L)).thenReturn(Optional.of(screening));
        when(movies.findByIdForShare(4L)).thenReturn(Optional.of(movie));
    }

    private void stubInventory(Set<SeatCoordinate> coordinates) {
        when(inventory.countByScreening_Id(8L)).thenReturn(120L);
        when(locks.lockSeats(8L, coordinates)).thenReturn(coordinates.stream()
                .map(coordinate -> new ScreeningSeat(screening, coordinate.row(), coordinate.number())).toList());
    }

    private HoldCreationResult create(List<ReservedSeatRequest> seats) {
        return service.create(1L, UUID.randomUUID(), new SeatHoldCreateRequest(8L, seats));
    }

    private void assertError(List<ReservedSeatRequest> seats, ErrorCode expected) {
        assertThatThrownBy(() -> create(seats)).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(expected);
    }
}
