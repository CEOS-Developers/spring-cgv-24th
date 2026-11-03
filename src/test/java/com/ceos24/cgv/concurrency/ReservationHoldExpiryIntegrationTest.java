package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.service.PaymentFacade;
import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.domain.schedule.entity.Schedule;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReservationHoldExpiryIntegrationTest extends ConcurrencyTestSupport {

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private PaymentFacade paymentFacade;

    @Test
    void 선점이_만료된_예매는_결제_승인이_거절되고_토스를_호출하지_않는다() {
        Schedule schedule = saveSchedule(1);
        Long userId = saveUser().getId();
        Long reservationId = createReservation(userId, schedule, seatIds().get(0));
        expire(reservationId);
        Payment payment = paymentRepository.findByReservationId(reservationId).orElseThrow();

        assertThatThrownBy(() -> paymentFacade.confirm(userId,
                new PaymentConfirmRequest("pk-expired", payment.getTossOrderId(), payment.getAmount())))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorReason().getCode()).isEqualTo("RESERVATION409_3"));

        verify(tossPaymentClient, never()).confirm(any(), any(), anyInt(), any());
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.READY);
    }

    @Test
    void 만료_해제는_결제_대기_예매만_만료시키고_결제_중이거나_확정된_예매는_유지한다() {
        Schedule schedule = saveSchedule(3);
        Long userId = saveUser().getId();
        List<Long> seatIds = seatIds();
        Long pendingId = createReservation(userId, schedule, seatIds.get(0));
        Long payingId = createReservation(userId, schedule, seatIds.get(1));
        Long reservedId = createReservation(userId, schedule, seatIds.get(2));
        expire(pendingId);
        expire(payingId, ReservationStatus.PAYING);
        expire(reservedId, ReservationStatus.RESERVED);

        reservationService.expireOverdueHolds();

        assertThat(reservationRepository.findById(pendingId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        assertThat(paymentRepository.findByReservationId(pendingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(pendingId)).isEmpty();
        assertThat(reservationRepository.findById(payingId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PAYING);
        assertThat(reservationRepository.findById(reservedId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(payingId)).hasSize(1);
        assertThat(reservationSeatRepository.findByReservationIdWithSeat(reservedId)).hasSize(1);
        assertThat(paymentRepository.findByReservationId(payingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.READY);
    }

    @Test
    void 만료된_선점_좌석은_다른_사용자가_예매할_수_있다() {
        Schedule schedule = saveSchedule(1);
        Long seatId = seatIds().get(0);
        Long firstReservationId = createReservation(saveUser().getId(), schedule, seatId);
        expire(firstReservationId);

        Long secondReservationId = createReservation(saveUser().getId(), schedule, seatId);

        assertThat(reservationRepository.findById(firstReservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservationRepository.findById(secondReservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservationSeatRepository.findByScheduleIdAndSeatIdIn(schedule.getId(), List.of(seatId)))
                .singleElement().satisfies(rs -> assertThat(rs.getReservation().getId()).isEqualTo(secondReservationId));
    }

    private List<Long> seatIds() {
        return seatRepository.findAll().stream().map(Seat::getId).sorted().toList();
    }

    private Long createReservation(Long userId, Schedule schedule, Long seatId) {
        return reservationService.create(userId, new ReservationCreateRequest(schedule.getId(), List.of(seatId)));
    }

    private void expire(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        ReflectionTestUtils.setField(reservation, "expiresAt", Instant.now().minusSeconds(60));
        reservationRepository.save(reservation);
    }

    private void expire(Long reservationId, ReservationStatus status) {
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        ReflectionTestUtils.setField(reservation, "expiresAt", Instant.now().minusSeconds(60));
        ReflectionTestUtils.setField(reservation, "status", status);
        reservationRepository.save(reservation);
    }
}
