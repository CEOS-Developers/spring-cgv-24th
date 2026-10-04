package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.reservation.dto.ReservationSnapshot;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.entity.ReservedSeat;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.service.seat.ScreeningSeatLockService;
import com.ceos.cgv.domain.reservation.value.SeatCoordinate;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock java.time.Clock clock;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ScreeningSeatLockService screeningSeatLockService;
    @InjectMocks
    private ReservationService reservationService;

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
