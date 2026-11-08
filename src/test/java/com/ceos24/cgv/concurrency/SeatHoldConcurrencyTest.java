package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.service.ReservationService;
import com.ceos24.cgv.domain.schedule.entity.Schedule;
import com.ceos24.cgv.domain.theater.entity.Seat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeatHoldConcurrencyTest extends ConcurrencyTestSupport {

    private static final int REQUESTS = 10;

    @Autowired
    private ReservationService reservationService;

    @Test
    void 같은_좌석을_동시에_예매하면_한_명만_선점한다() throws InterruptedException {
        Schedule schedule = saveSchedule(1);
        Seat seat = seatRepository.findAll().get(0);
        ReservationCreateRequest request = new ReservationCreateRequest(schedule.getId(), List.of(seat.getId()));
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            Long userId = saveUser().getId();
            tasks.add(() -> reservationService.create(userId, request));
        }

        List<Throwable> results = runConcurrently(tasks);

        long seatRows = reservationSeatRepository.findByScheduleIdAndSeatIdIn(schedule.getId(), List.of(seat.getId()))
                .size();
        List<Reservation> reservations = reservationRepository.findAll();
        long pendingReservations = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING_PAYMENT).count();
        System.out.printf("[좌석선점] 요청 %d, 성공 %d, 실패(RESERVATION409) %d, 좌석행 %d, 예매 %d, 결제 %d, 실패유형 %s%n",
                REQUESTS, successCount(results), failureCount(results, "RESERVATION409"), seatRows,
                pendingReservations, paymentRepository.count(), failureCounts(results));

        assertThat(successCount(results)).isEqualTo(1);
        assertThat(failureCount(results, "RESERVATION409")).isEqualTo(REQUESTS - 1);
        assertThat(seatRows).isEqualTo(1);
        assertThat(pendingReservations).isEqualTo(1);
        assertThat(reservations).hasSize(1);
        assertThat(paymentRepository.count()).isEqualTo(1);
    }
}
