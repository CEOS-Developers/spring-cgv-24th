package com.spring_cgv_24th.domain.reservation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.domain.reservation.dto.ReservationReqDTO;
import com.spring_cgv_24th.domain.reservation.entity.Reservation;
import com.spring_cgv_24th.domain.reservation.entity.ReservationSeat;
import com.spring_cgv_24th.domain.reservation.enums.ReservationStatus;
import com.spring_cgv_24th.domain.reservation.repository.ReservationRepository;
import com.spring_cgv_24th.domain.reservation.repository.ReservationSeatRepository;
import com.spring_cgv_24th.domain.screening.entity.Screening;
import com.spring_cgv_24th.domain.screening.entity.ScreeningSeat;
import com.spring_cgv_24th.domain.screening.repository.ScreeningRepository;
import com.spring_cgv_24th.domain.screening.repository.ScreeningSeatRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 12, 0);
    private static final long MEMBER_ID = 7L;

    @Mock private MemberRepository memberRepository;
    @Mock private ScreeningRepository screeningRepository;
    @Mock private ScreeningSeatRepository screeningSeatRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationSeatRepository reservationSeatRepository;
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        reservationService = new ReservationService(
                memberRepository, screeningRepository, screeningSeatRepository,
                reservationRepository, reservationSeatRepository, clock);
    }

    @Test
    void occupiedSeatCannotBeReservedAgain() {
        Screening screening = mock(Screening.class);
        when(screening.getId()).thenReturn(1L);
        when(screening.getStartsAt()).thenReturn(NOW.plusHours(1));
        ScreeningSeat seat = seat(screening);
        Reservation existing = Reservation.builder().screening(screening).totalPrice(14_000).build();
        seat.occupy(existing);
        when(screeningRepository.findById(1L)).thenReturn(Optional.of(screening));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(mock(Member.class)));
        when(screeningSeatRepository.findByIdAndScreeningIdForUpdate(2L, 1L))
                .thenReturn(Optional.of(seat));

        CustomException error = assertThrows(CustomException.class,
                () -> reservationService.createReservation(
                        new ReservationReqDTO.CreateReservationDTO(MEMBER_ID, 1L, List.of(2L))));

        assertEquals(ErrorCode.SEAT_ALREADY_RESERVED, error.getErrorCode());
        assertSame(existing, seat.getReservation());
        verifyNoInteractions(reservationRepository, reservationSeatRepository);
    }

    @Test
    void reservationIsAllowedBeforeScreeningStarts() {
        Screening screening = mock(Screening.class);
        when(screening.getId()).thenReturn(1L);
        when(screening.getStartsAt()).thenReturn(NOW.plusSeconds(1));
        ScreeningSeat seat = seat(screening);
        Member member = mock(Member.class);
        when(screeningRepository.findById(1L)).thenReturn(Optional.of(screening));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(screeningSeatRepository.findByIdAndScreeningIdForUpdate(2L, 1L))
                .thenReturn(Optional.of(seat));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = reservationService.createReservation(
                new ReservationReqDTO.CreateReservationDTO(MEMBER_ID, 1L, List.of(2L)));

        assertNotNull(seat.getReservation());
        assertSame(member, seat.getReservation().getMember());
        assertEquals(ReservationStatus.CONFIRMED, response.status());
        assertEquals(14_000L, response.totalPrice());
        assertEquals(14_000L, seat.getReservation().getTotalPrice());
        verify(reservationSeatRepository).saveAll(any());
    }

    @Test
    void nonexistentMemberCannotReserve() {
        Screening screening = mock(Screening.class);
        when(screening.getStartsAt()).thenReturn(NOW.plusHours(1));
        when(screeningRepository.findById(1L)).thenReturn(Optional.of(screening));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        CustomException error = assertThrows(CustomException.class,
                () -> reservationService.createReservation(
                        new ReservationReqDTO.CreateReservationDTO(MEMBER_ID, 1L, List.of(2L))));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(screeningSeatRepository, reservationRepository, reservationSeatRepository);
    }

    @Test
    void reservationAtScreeningStartIsRejected() {
        Screening screening = mock(Screening.class);
        when(screening.getStartsAt()).thenReturn(NOW);
        when(screeningRepository.findById(1L)).thenReturn(Optional.of(screening));

        CustomException error = assertThrows(CustomException.class,
                () -> reservationService.createReservation(
                        new ReservationReqDTO.CreateReservationDTO(MEMBER_ID, 1L, List.of(2L))));

        assertEquals(ErrorCode.SCREENING_ALREADY_STARTED, error.getErrorCode());
        verifyNoInteractions(screeningSeatRepository, reservationRepository, reservationSeatRepository);
    }

    @Test
    void pastScreeningCannotBeReserved() {
        Screening screening = mock(Screening.class);
        when(screening.getStartsAt()).thenReturn(NOW.minusHours(3));
        when(screeningRepository.findById(1L)).thenReturn(Optional.of(screening));

        CustomException error = assertThrows(CustomException.class,
                () -> reservationService.createReservation(
                        new ReservationReqDTO.CreateReservationDTO(MEMBER_ID, 1L, List.of(2L))));

        assertEquals(ErrorCode.SCREENING_ALREADY_STARTED, error.getErrorCode());
        verifyNoInteractions(screeningSeatRepository, reservationRepository, reservationSeatRepository);
    }

    @Test
    void reservationIsRejectedIfScreeningStartsWhileWaitingForSeatLock() {
        Clock advancingClock = mock(Clock.class);
        Instant start = NOW.atZone(ZONE).toInstant();
        when(advancingClock.instant()).thenReturn(start.minusSeconds(1), start);
        when(advancingClock.getZone()).thenReturn(ZONE);
        ReservationService service = new ReservationService(
                memberRepository, screeningRepository, screeningSeatRepository,
                reservationRepository, reservationSeatRepository, advancingClock);
        Screening screening = mock(Screening.class);
        when(screening.getId()).thenReturn(1L);
        when(screening.getStartsAt()).thenReturn(NOW);
        ScreeningSeat seat = seat(screening);
        when(screeningRepository.findById(1L)).thenReturn(Optional.of(screening));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(mock(Member.class)));
        when(screeningSeatRepository.findByIdAndScreeningIdForUpdate(2L, 1L))
                .thenReturn(Optional.of(seat));

        CustomException error = assertThrows(CustomException.class,
                () -> service.createReservation(
                        new ReservationReqDTO.CreateReservationDTO(MEMBER_ID, 1L, List.of(2L))));

        assertEquals(ErrorCode.SCREENING_ALREADY_STARTED, error.getErrorCode());
        assertNull(seat.getReservation());
        verifyNoInteractions(reservationRepository, reservationSeatRepository);
    }

    @Test
    void cancellationReleasesSeatsAndChangesStatus() {
        Screening screening = mock(Screening.class);
        when(screening.getStartsAt()).thenReturn(NOW.plusHours(1));
        Reservation reservation = Reservation.builder().member(owner()).screening(screening)
                .totalPrice(28_000).build();
        ScreeningSeat first = seat(screening);
        ScreeningSeat second = seat(screening);
        first.occupy(reservation);
        second.occupy(reservation);
        when(reservationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservation));
        when(screeningSeatRepository.findAllByReservationIdForUpdate(1L))
                .thenReturn(List.of(first, second));

        reservationService.cancelReservation(1L, MEMBER_ID);

        assertNull(first.getReservation());
        assertNull(second.getReservation());
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals(NOW, reservation.getCancelledAt());
        assertEquals(28_000L, reservation.getTotalPrice());
        verifyNoInteractions(reservationSeatRepository);
    }

    @Test
    void cancellationKeepsSeatHistoryAndAllowsAnotherReservation() {
        Screening screening = mock(Screening.class);
        when(screening.getId()).thenReturn(1L);
        when(screening.getStartsAt()).thenReturn(NOW.plusHours(1));
        ScreeningSeat seat = seat(screening);
        Member member = owner();
        List<ReservationSeat> history = new ArrayList<>();
        when(screeningRepository.findById(1L)).thenReturn(Optional.of(screening));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(screeningSeatRepository.findByIdAndScreeningIdForUpdate(2L, 1L))
                .thenReturn(Optional.of(seat));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationSeatRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ReservationSeat> rows = invocation.getArgument(0);
            history.addAll(rows);
            return rows;
        });

        reservationService.createReservation(
                new ReservationReqDTO.CreateReservationDTO(MEMBER_ID, 1L, List.of(2L)));
        Reservation firstReservation = seat.getReservation();
        when(reservationRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(firstReservation));
        when(screeningSeatRepository.findAllByReservationIdForUpdate(1L))
                .thenReturn(List.of(seat));

        reservationService.cancelReservation(1L, MEMBER_ID);

        assertNull(seat.getReservation());
        assertEquals(ReservationStatus.CANCELLED, firstReservation.getStatus());
        assertEquals(14_000L, firstReservation.getTotalPrice());
        assertEquals(1, history.size());
        assertSame(firstReservation, history.getFirst().getReservation());
        assertSame(seat, history.getFirst().getScreeningSeat());
        assertEquals(14_000, history.getFirst().getPrice());

        reservationService.createReservation(
                new ReservationReqDTO.CreateReservationDTO(MEMBER_ID, 1L, List.of(2L)));

        assertNotSame(firstReservation, seat.getReservation());
        assertEquals(2, history.size());
        assertSame(firstReservation, history.getFirst().getReservation());
        assertSame(seat.getReservation(), history.get(1).getReservation());
        verify(reservationSeatRepository, times(2)).saveAll(any());
    }

    @Test
    void cancellationAtScreeningStartIsRejectedWithoutReleasingSeats() {
        Screening screening = mock(Screening.class);
        when(screening.getStartsAt()).thenReturn(NOW);
        Reservation reservation = Reservation.builder().member(owner()).screening(screening)
                .totalPrice(14_000).build();
        ScreeningSeat seat = seat(screening);
        seat.occupy(reservation);
        when(reservationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservation));

        CustomException error = assertThrows(CustomException.class,
                () -> reservationService.cancelReservation(1L, MEMBER_ID));

        assertEquals(ErrorCode.RESERVATION_CANCELLATION_CLOSED, error.getErrorCode());
        assertSame(reservation, seat.getReservation());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertNull(reservation.getCancelledAt());
        verifyNoInteractions(screeningSeatRepository);
    }

    @Test
    void cancellationIsRejectedIfScreeningStartsWhileWaitingForSeatLock() {
        Clock advancingClock = mock(Clock.class);
        Instant start = NOW.atZone(ZONE).toInstant();
        when(advancingClock.instant()).thenReturn(start.minusSeconds(1), start);
        when(advancingClock.getZone()).thenReturn(ZONE);
        ReservationService service = new ReservationService(
                memberRepository, screeningRepository, screeningSeatRepository,
                reservationRepository, reservationSeatRepository, advancingClock);
        Screening screening = mock(Screening.class);
        when(screening.getStartsAt()).thenReturn(NOW);
        Reservation reservation = Reservation.builder().member(owner()).screening(screening)
                .totalPrice(14_000).build();
        ScreeningSeat seat = seat(screening);
        seat.occupy(reservation);
        when(reservationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservation));
        when(screeningSeatRepository.findAllByReservationIdForUpdate(1L)).thenReturn(List.of(seat));

        CustomException error = assertThrows(CustomException.class,
                () -> service.cancelReservation(1L, MEMBER_ID));

        assertEquals(ErrorCode.RESERVATION_CANCELLATION_CLOSED, error.getErrorCode());
        assertSame(reservation, seat.getReservation());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertNull(reservation.getCancelledAt());
    }

    @Test
    void anotherMemberCannotCancelReservation() {
        Screening screening = mock(Screening.class);
        Reservation reservation = Reservation.builder().member(owner()).screening(screening)
                .totalPrice(14_000).build();
        when(reservationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservation));

        CustomException error = assertThrows(CustomException.class,
                () -> reservationService.cancelReservation(1L, MEMBER_ID + 1));

        assertEquals(ErrorCode.RESERVATION_FORBIDDEN, error.getErrorCode());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        verifyNoInteractions(screeningSeatRepository);
    }

    @Test
    void reservationWithoutOwnerCannotBeCancelledByMemberId() {
        Reservation reservation = Reservation.builder().screening(mock(Screening.class))
                .totalPrice(14_000).build();
        when(reservationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservation));

        CustomException error = assertThrows(CustomException.class,
                () -> reservationService.cancelReservation(1L, MEMBER_ID));

        assertEquals(ErrorCode.RESERVATION_FORBIDDEN, error.getErrorCode());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        verifyNoInteractions(screeningSeatRepository);
    }

    private static Member owner() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(MEMBER_ID);
        return member;
    }

    private static ScreeningSeat seat(Screening screening) {
        return ScreeningSeat.builder()
                .screening(screening)
                .rowNo((short) 1)
                .columnNo((short) 1)
                .price(14_000)
                .build();
    }
}
