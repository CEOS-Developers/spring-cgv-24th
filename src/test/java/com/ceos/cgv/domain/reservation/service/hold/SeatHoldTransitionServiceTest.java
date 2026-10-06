package com.ceos.cgv.domain.reservation.service.hold;

import com.ceos.cgv.domain.reservation.dto.ReservationSnapshot;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.service.result.HoldTransitionResult;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatHoldTransitionServiceTest {
    @Mock ReservationRepository reservations;
    @InjectMocks SeatHoldTransitionService service;

    @Test
    void 확정_조회_사이에_취소된_진행_건을_확정_성공으로_반환하지_않는다() {
        when(reservations.findSnapshotById(12L)).thenReturn(Optional.of(
                new ReservationSnapshot(7L, 8L, 9L, ReservationStatus.RESERVED, null)));
        Reservation canceled = mock(Reservation.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(canceled.getUser()).thenReturn(user);
        when(canceled.getStatus()).thenReturn(ReservationStatus.CANCELED);
        when(reservations.findWithSeatsById(12L)).thenReturn(Optional.of(canceled));

        assertThat(service.confirm(12L, 7L)).isEqualTo(new HoldTransitionResult.Failure(ErrorCode.HOLD_NOT_ACTIVE));
    }
}
