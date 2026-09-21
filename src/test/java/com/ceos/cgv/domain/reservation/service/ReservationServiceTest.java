package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
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

import java.util.List;
import java.util.Optional;

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
    private ReservationRepository reservationRepository;
    @Mock
    private ReservedSeatRepository reservedSeatRepository;
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
        given(screeningRepository.findByIdWithLock(8L)).willReturn(Optional.of(screening));
        given(reservedSeatRepository.findIdByReservationScreeningIdAndSeatRowAndSeatNumberAndReservationStatus(
                8L, "A", 1, ReservationStatus.RESERVED
        )).willReturn(Optional.of(100L));
        ReservationCreateRequest request = new ReservationCreateRequest(
                1L, 8L, List.of(new ReservedSeatRequest("A", 1))
        );

        assertThatThrownBy(() -> reservationService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED);

        then(reservationRepository).shouldHaveNoInteractions();
    }
}
