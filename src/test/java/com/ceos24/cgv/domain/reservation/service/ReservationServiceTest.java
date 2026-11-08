package com.ceos24.cgv.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.entity.ScreeningSeat;
import com.ceos24.cgv.domain.screening.repository.ScreeningSeatRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ScreeningSeatRepository seatRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    @DisplayName("reserveSeat: 성공")
    void reserveSeat_Success() {
        // given
        Long memberId = 1L;
        Long screeningId = 1L;
        Long seatNumber = 10L;

        Screening screening = new Screening(null, null, null, null);
        ScreeningSeat seat = ScreeningSeat.create(screening, seatNumber);
        Member member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(memberId);

        given(seatRepository.findByScreeningIdAndSeatNumber(screeningId, seatNumber))
                .willReturn(Optional.of(seat));
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        // when
        reservationService.reserveSeat(memberId, screeningId, seatNumber);

        // then
        assertThat(seat.getIsReserved()).isTrue();
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("reserveSeat: 좌석이 존재하지 않을 때 예외 발생")
    void reserveSeat_SeatNotFound() {
        // given
        Long memberId = 1L;
        Long screeningId = 1L;
        Long seatNumber = 10L;

        given(seatRepository.findByScreeningIdAndSeatNumber(screeningId, seatNumber))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reservationService.reserveSeat(memberId, screeningId, seatNumber))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SEAT_NOT_FOUND);
    }

    @Test
    @DisplayName("reserveSeat: 회원이 존재하지 않을 때 예외 발생")
    void reserveSeat_MemberNotFound() {
        // given
        Long memberId = 1L;
        Long screeningId = 1L;
        Long seatNumber = 10L;

        Screening screening = new Screening(null, null, null, null);
        ScreeningSeat seat = ScreeningSeat.create(screening, seatNumber);

        given(seatRepository.findByScreeningIdAndSeatNumber(screeningId, seatNumber))
                .willReturn(Optional.of(seat));
        given(memberRepository.findById(memberId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reservationService.reserveSeat(memberId, screeningId, seatNumber))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("cancelReservation: 성공")
    void cancelReservation_Success() {
        // given
        Long memberId = 1L;
        Long screeningId = 1L;
        Long seatNumber = 10L;

        Screening screening = new Screening(null, null, null, null);
        ScreeningSeat seat = ScreeningSeat.create(screening, seatNumber);
        seat.reserveSeat(); // 예약된 상태로 설정
        Member member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(memberId);

        Reservation reservation = Reservation.create(member, seat);

        given(reservationRepository.findReservationToCancel(memberId, screeningId, seatNumber))
                .willReturn(Optional.of(reservation));

        // when
        reservationService.cancelReservation(memberId, screeningId, seatNumber);

        // then
        assertThat(seat.getIsReserved()).isFalse();
        verify(reservationRepository).delete(reservation);
    }

    @Test
    @DisplayName("cancelReservation: 예약이 존재하지 않을 때 예외 발생")
    void cancelReservation_ReservationNotFound() {
        // given
        Long memberId = 1L;
        Long screeningId = 1L;
        Long seatNumber = 10L;

        given(reservationRepository.findReservationToCancel(memberId, screeningId, seatNumber))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reservationService.cancelReservation(memberId, screeningId, seatNumber))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_NOT_FOUND);
    }
}
