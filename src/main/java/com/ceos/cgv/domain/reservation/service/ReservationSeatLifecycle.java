package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import java.time.Instant;
import java.util.List;

// 호출자의 트랜잭션과 좌석 잠금 안에서 상태와 점유를 함께 변경한다.
final class ReservationSeatLifecycle {
    private ReservationSeatLifecycle() {
    }

    static void verifyOccupancy(List<ScreeningSeat> seats, Reservation reservation) {
        for (ScreeningSeat seat : seats) {
            if (seat.getCurrentReservation() == null
                    || !seat.getCurrentReservation().getId().equals(reservation.getId())) {
                throw new BusinessException(ErrorCode.SEAT_OWNER_MISMATCH);
            }
        }
    }

    static void expire(Reservation reservation, List<ScreeningSeat> seats, Instant now) {
        verifyOccupancy(seats, reservation);
        reservation.expire(now);
        seats.forEach(seat -> seat.releaseIfOwnedBy(reservation));
    }

    static void release(Reservation reservation, List<ScreeningSeat> seats) {
        verifyOccupancy(seats, reservation);
        reservation.release();
        seats.forEach(seat -> seat.releaseIfOwnedBy(reservation));
    }

    static void cancel(Reservation reservation, List<ScreeningSeat> seats) {
        verifyOccupancy(seats, reservation);
        reservation.cancel();
        seats.forEach(seat -> seat.releaseIfOwnedBy(reservation));
    }
}
