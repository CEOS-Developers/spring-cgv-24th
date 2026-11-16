package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.payment.dto.response.PaymentReadyResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.exception.ReservationErrorStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.schedule.repository.ScheduleRepository;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T01:00:00Z");

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationSeatRepository reservationSeatRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentRepository paymentRepository;

    private Clock clock;
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        reservationService = new ReservationService(reservationRepository, reservationSeatRepository,
                scheduleRepository, seatRepository, userRepository, paymentRepository, clock);
    }

    @Test
    void 좌석을_선점하면_결제대기_예매와_결제가_생성된다() {
        var user = user(1L);
        var screen = screen(1L, theater(1L));
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusHours(1));
        var firstSeat = seat(10L, screen, 1, 1);
        var secondSeat = seat(11L, screen, 1, 2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(seatRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(firstSeat, secondSeat));
        when(reservationSeatRepository.findByScheduleIdAndSeatIdIn(1L, List.of(10L, 11L))).thenReturn(List.of());
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 100L));

        Long result = reservationService.create(1L, new ReservationCreateRequest(1L, List.of(10L, 11L)));

        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        verify(paymentRepository).save(paymentCaptor.capture());
        Reservation saved = reservationCaptor.getValue();
        assertThat(result).isEqualTo(100L);
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(saved.getReservedAt()).isEqualTo(NOW);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(saved.getTotalPrice()).isEqualTo(30_000);
        assertThat(saved.getSeatSummary()).isEqualTo("A1, A2");
        assertThat(paymentCaptor.getValue().getAmount()).isEqualTo(30_000);
        assertThat(paymentCaptor.getValue().getOrderName()).isEqualTo("테스트 영화 2매");
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void 좌석은_아이디_오름차순으로_저장한다() {
        var screen = screen(1L, theater(1L));
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusHours(1));
        var firstSeat = seat(10L, screen, 1, 1);
        var secondSeat = seat(11L, screen, 1, 2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(seatRepository.findAllById(List.of(11L, 10L))).thenReturn(List.of(secondSeat, firstSeat));
        when(reservationSeatRepository.findByScheduleIdAndSeatIdIn(1L, List.of(11L, 10L))).thenReturn(List.of());
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 100L));

        reservationService.create(1L, new ReservationCreateRequest(1L, List.of(11L, 10L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReservationSeat>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationSeatRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).extracting(rs -> rs.getSeat().getId()).containsExactly(10L, 11L);
    }

    @Test
    void 요청_좌석에_만료된_선점이_있으면_해제하고_새로_선점한다() {
        var user = user(1L);
        var screen = screen(1L, theater(1L));
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusHours(1));
        var seat = seat(10L, screen, 1, 1);
        var expired = withId(Reservation.create(user(2L), schedule, 15_000, "A1", NOW.minus(Duration.ofMinutes(6))), 50L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(seatRepository.findAllById(List.of(10L))).thenReturn(List.of(seat));
        when(reservationSeatRepository.findByScheduleIdAndSeatIdIn(1L, List.of(10L)))
                .thenReturn(List.of(reservationSeat(200L, expired, seat)));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 100L));

        Long result = reservationService.create(1L, new ReservationCreateRequest(1L, List.of(10L)));

        assertThat(result).isEqualTo(100L);
        verify(reservationRepository).expireHolds(List.of(50L), NOW);
        verify(reservationSeatRepository).deleteByExpiredReservationIds(List.of(50L));
        verify(paymentRepository).expireByExpiredReservationIds(List.of(50L));
        verify(reservationSeatRepository).saveAllAndFlush(any());
    }

    @Test
    void 유효한_선점이_있으면_예매할_수_없다() {
        var screen = screen(1L, theater(1L));
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusHours(1));
        var seat = seat(10L, screen, 1, 1);
        var holding = withId(Reservation.create(user(2L), schedule, 15_000, "A1", NOW.minus(Duration.ofMinutes(1))), 50L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(seatRepository.findAllById(List.of(10L))).thenReturn(List.of(seat));
        when(reservationSeatRepository.findByScheduleIdAndSeatIdIn(1L, List.of(10L)))
                .thenReturn(List.of(reservationSeat(200L, holding, seat)));

        assertThatThrownBy(() -> reservationService.create(1L, new ReservationCreateRequest(1L, List.of(10L))))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.SEAT_ALREADY_RESERVED);
        verify(reservationRepository, never()).expireHolds(any(), any());
        verify(reservationRepository, never()).save(any());
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void 좌석_저장중_유니크_제약_위반이_발생하면_이미_예매된_좌석으로_처리한다() {
        var screen = screen(1L, theater(1L));
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusHours(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(seatRepository.findAllById(List.of(10L))).thenReturn(List.of(seat(10L, screen, 1, 1)));
        when(reservationSeatRepository.findByScheduleIdAndSeatIdIn(1L, List.of(10L))).thenReturn(List.of());
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 100L));
        doThrow(new DataIntegrityViolationException("duplicate")).when(reservationSeatRepository).saveAllAndFlush(any());

        assertThatThrownBy(() -> reservationService.create(1L, new ReservationCreateRequest(1L, List.of(10L))))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.SEAT_ALREADY_RESERVED);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void 중복_좌석을_요청하면_조회없이_예외가_발생한다() {
        assertThatThrownBy(() -> reservationService.create(1L, new ReservationCreateRequest(1L, List.of(10L, 10L))))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.DUPLICATE_SEAT_REQUEST);
        verifyNoInteractions(userRepository, scheduleRepository, seatRepository, reservationRepository,
                reservationSeatRepository, paymentRepository);
    }

    @Test
    void 이미_상영이_시작된_회차는_예매할_수_없다() {
        var theater = theater(1L);
        var screen = screen(1L, theater);
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).minusMinutes(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> reservationService.create(1L, new ReservationCreateRequest(1L, List.of(10L))))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.RESERVATION_CLOSED);
        verifyNoInteractions(seatRepository, reservationRepository, reservationSeatRepository);
    }

    @Test
    void 본인의_예매_상세를_좌석정보와_함께_조회한다() {
        var user = user(1L);
        var theater = theater(1L);
        var screen = screen(1L, theater);
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusHours(1));
        var reservation = reservation(100L, user, schedule, "A1");
        var seat = seat(10L, screen, 1, 1);
        when(reservationRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(reservation));
        when(reservationSeatRepository.findByReservationIdWithSeat(100L))
                .thenReturn(List.of(reservationSeat(200L, reservation, seat)));

        ReservationResponse result = reservationService.findById(100L, 1L);

        assertThat(result.reservationId()).isEqualTo(100L);
        assertThat(result.seatSummary()).isEqualTo("A1");
        assertThat(result.seats()).hasSize(1);
        assertThat(result.seats().getFirst().seatId()).isEqualTo(10L);
        assertThat(result.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    void 결제_대기_예매를_취소하면_좌석점유를_삭제하고_결제를_만료시킨다() {
        var user = user(1L);
        var screen = screen(1L, theater(1L));
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusMinutes(30));
        var reservation = reservation(100L, user, schedule, "A1");
        var payment = Payment.forReservation(reservation, "테스트 영화 1매");
        when(reservationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(reservation));
        when(paymentRepository.findByReservationId(100L)).thenReturn(Optional.of(payment));

        reservationService.cancel(100L, 1L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(reservationSeatRepository).deleteByReservationId(100L);
    }

    @Test
    void 결제_완료된_예매는_결제_취소_API로_취소해야_한다() {
        var reservation = reservationWithStatus(ReservationStatus.RESERVED);
        when(reservationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(reservation));
        when(paymentRepository.findByReservationId(100L))
                .thenReturn(Optional.of(Payment.forReservation(reservation, "테스트 영화 1매")));

        assertThatThrownBy(() -> reservationService.cancel(100L, 1L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.PAYMENT_CANCEL_REQUIRED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        verify(reservationSeatRepository, never()).deleteByReservationId(any());
    }

    @Test
    void 결제_기록이_없는_예매완료_건은_기존대로_취소한다() {
        var reservation = reservationWithStatus(ReservationStatus.RESERVED);
        when(reservationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(reservation));
        when(paymentRepository.findByReservationId(100L)).thenReturn(Optional.empty());

        reservationService.cancel(100L, 1L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationSeatRepository).deleteByReservationId(100L);
    }

    @Test
    void 결제가_진행_중인_예매는_취소할_수_없다() {
        var reservation = reservationWithStatus(ReservationStatus.PAYING);
        when(reservationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(100L, 1L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.RESERVATION_PAYMENT_IN_PROGRESS);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAYING);
        verify(reservationSeatRepository, never()).deleteByReservationId(any());
    }

    @Test
    void 선점이_만료되어_결제_진행_전환에_실패하면_예외가_발생한다() {
        when(reservationRepository.startPaying(100L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> reservationService.startPaying(100L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.RESERVATION_HOLD_EXPIRED);
    }

    @Test
    void 이미_결제가_진행_중인_예매에_결제를_시작하면_이미_진행_중이라는_예외가_발생한다() {
        var schedule = schedule(1L, movie(1L), screen(1L, theater(1L)), LocalDateTime.now(clock).plusHours(1));
        var reservation = reservation(100L, user(1L), schedule, "A1");
        ReflectionTestUtils.setField(reservation, "status", ReservationStatus.PAYING);
        when(reservationRepository.startPaying(100L, NOW)).thenReturn(0);
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.startPaying(100L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.RESERVATION_ALREADY_PAYING);
    }

    @Test
    void 결제_진행_중이_아닌_예매의_결제_진행_전환이_실패하면_선점_만료_예외가_발생한다() {
        var schedule = schedule(1L, movie(1L), screen(1L, theater(1L)), LocalDateTime.now(clock).plusHours(1));
        var reservation = reservation(100L, user(1L), schedule, "A1");
        when(reservationRepository.startPaying(100L, NOW)).thenReturn(0);
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.startPaying(100L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.RESERVATION_HOLD_EXPIRED);
    }

    @Test
    void 선점이_유효하면_결제_진행으로_전환한다() {
        when(reservationRepository.startPaying(100L, NOW)).thenReturn(1);

        reservationService.startPaying(100L);

        verify(reservationRepository).startPaying(100L, NOW);
    }

    @Test
    void 결제_진행_중인_예매를_결제_완료로_확정한다() {
        var reservation = reservationWithStatus(ReservationStatus.PAYING);

        reservationService.confirmPaid(reservation);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void 결제_진행_중이_아닌_예매는_확정할_수_없다() {
        var reservation = reservationWithStatus(ReservationStatus.CANCELLED);

        assertThatThrownBy(() -> reservationService.confirmPaid(reservation))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void 결제_진행_중인_예매는_미결제_해제시_취소하고_좌석을_삭제한다() {
        var reservation = reservationWithStatus(ReservationStatus.PAYING);

        reservationService.releaseUnpaid(reservation);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationSeatRepository).deleteByReservationId(100L);
    }

    @Test
    void 결제_진행_중이_아닌_예매는_미결제_해제를_해도_바뀌지_않는다() {
        var reservation = reservationWithStatus(ReservationStatus.RESERVED);

        reservationService.releaseUnpaid(reservation);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        verifyNoInteractions(reservationSeatRepository);
    }

    @Test
    void 예매_완료된_예매는_결제_취소시_취소하고_좌석을_삭제한다() {
        var reservation = reservationWithStatus(ReservationStatus.RESERVED);

        reservationService.releaseCanceled(reservation);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationSeatRepository).deleteByReservationId(100L);
    }

    @Test
    void 결제_취소_해제를_중복_호출해도_한_번만_동작한다() {
        var reservation = reservationWithStatus(ReservationStatus.RESERVED);

        reservationService.releaseCanceled(reservation);
        reservationService.releaseCanceled(reservation);

        verify(reservationSeatRepository, times(1)).deleteByReservationId(100L);
    }

    @Test
    void 상영_20분_전이_지나면_결제_취소를_검증에서_거절한다() {
        var reservation = reservationWithStatus(ReservationStatus.RESERVED);
        ReflectionTestUtils.setField(reservation.getSchedule(), "startTime", LocalDateTime.now(clock).plusMinutes(10));

        assertThatThrownBy(() -> reservationService.validateCancelable(reservation))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.CANCEL_DEADLINE_PASSED);
    }

    private Reservation reservationWithStatus(ReservationStatus status) {
        var screen = screen(1L, theater(1L));
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusMinutes(30));
        var reservation = reservation(100L, user(1L), schedule, "A1");
        ReflectionTestUtils.setField(reservation, "status", status);
        return reservation;
    }

    @Test
    void 취소마감이_지나면_예매를_취소할_수_없다() {
        var user = user(1L);
        var theater = theater(1L);
        var screen = screen(1L, theater);
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusMinutes(10));
        var reservation = reservation(100L, user, schedule, "A1");
        when(reservationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(100L, 1L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.CANCEL_DEADLINE_PASSED);
        verify(reservationSeatRepository, never()).deleteByReservationId(any());
    }

    @Test
    void 결제_준비_정보를_조회한다() {
        var screen = screen(1L, theater(1L));
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusHours(1));
        var reservation = withId(Reservation.create(user(1L), schedule, 15_000, "A1", NOW), 100L);
        var payment = Payment.forReservation(reservation, "테스트 영화 1매");
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
        when(paymentRepository.findByReservationId(100L)).thenReturn(Optional.of(payment));

        PaymentReadyResponse result = reservationService.getPaymentInfo(100L, 1L);

        assertThat(result.orderId()).isEqualTo(payment.getTossOrderId());
        assertThat(result.amount()).isEqualTo(15_000);
        assertThat(result.orderName()).isEqualTo("테스트 영화 1매");
    }

    @Test
    void 만료된_선점은_결제_준비_정보를_조회할_수_없다() {
        var screen = screen(1L, theater(1L));
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusHours(1));
        var reservation = withId(Reservation.create(user(1L), schedule, 15_000, "A1", NOW.minus(Duration.ofMinutes(5))), 100L);
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.getPaymentInfo(100L, 1L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.RESERVATION_NOT_PENDING_PAYMENT);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void 남의_예매는_결제_준비_정보를_조회할_수_없다() {
        var screen = screen(1L, theater(1L));
        var schedule = schedule(1L, movie(1L), screen, LocalDateTime.now(clock).plusHours(1));
        var reservation = withId(Reservation.create(user(2L), schedule, 15_000, "A1", NOW), 100L);
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.getPaymentInfo(100L, 1L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(ReservationErrorStatus.NOT_RESERVATION_OWNER);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void 만료_대상_선점을_조회해_해제한다() {
        when(reservationRepository.findExpiredHoldIds(NOW)).thenReturn(List.of(1L, 2L));
        when(reservationRepository.expireHolds(List.of(1L, 2L), NOW)).thenReturn(2);

        reservationService.expireOverdueHolds();

        verify(reservationSeatRepository).deleteByExpiredReservationIds(List.of(1L, 2L));
        verify(paymentRepository).expireByExpiredReservationIds(List.of(1L, 2L));
    }

    @Test
    void 만료_대상이_없으면_해제_쿼리를_호출하지_않는다() {
        when(reservationRepository.findExpiredHoldIds(NOW)).thenReturn(List.of());

        reservationService.expireOverdueHolds();

        verify(reservationRepository, never()).expireHolds(any(), any());
        verifyNoInteractions(reservationSeatRepository, paymentRepository);
    }
}
