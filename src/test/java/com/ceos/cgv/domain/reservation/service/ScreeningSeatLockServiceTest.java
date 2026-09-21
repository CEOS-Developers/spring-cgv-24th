package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningSeatLockServiceTest {
    @Mock ScreeningSeatRepository repository;
    @InjectMocks ScreeningSeatLockService lockService;

    @Test
    void 요청_순서와_무관하게_회차_좌석을_좌표_순으로_잠근다() {
        when(repository.lockCoordinateNowait(8L, "A", 1)).thenReturn(Optional.of(mock(ScreeningSeat.class)));
        when(repository.lockCoordinateNowait(8L, "A", 2)).thenReturn(Optional.of(mock(ScreeningSeat.class)));
        when(repository.lockCoordinateNowait(8L, "B", 1)).thenReturn(Optional.of(mock(ScreeningSeat.class)));

        lockService.lockSeats(8L, List.of(
                new SeatCoordinate("B", 1), new SeatCoordinate("A", 2), new SeatCoordinate("A", 1)));

        var order = inOrder(repository);
        order.verify(repository).lockCoordinateNowait(8L, "A", 1);
        order.verify(repository).lockCoordinateNowait(8L, "A", 2);
        order.verify(repository).lockCoordinateNowait(8L, "B", 1);
    }

    @Test
    void 잠금_획득에_실패하면_좌석_처리중_오류로_변환한다() {
        when(repository.lockCoordinateNowait(8L, "A", 1))
                .thenThrow(new CannotAcquireLockException("NOWAIT conflict"));

        assertThatThrownBy(() -> lockService.lockSeats(8L, List.of(new SeatCoordinate("A", 1))))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_BUSY);
    }
}
