package com.ceos.cgv.domain.reservation.service.seat;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.service.exception.ExpiredHoldEncountered;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import java.time.Instant;
import java.util.List;

public final class SeatOccupancyGuard {
    private SeatOccupancyGuard() {
    }

    // 호출자는 먼저 모든 요청 좌석을 정해진 순서로 잠가야 한다.
    public static void ensureAvailable(List<ScreeningSeat> lockedSeats, Instant now) {
        for (ScreeningSeat seat : lockedSeats) {
            Reservation occupant = seat.getCurrentReservation();
            if (occupant == null) {
                continue;
            }
            if (occupant.isExpiredAt(now)) {
                throw new ExpiredHoldEncountered(occupant.getId(), false);
            }
            ErrorCode error = switch (occupant.getStatus()) {
                case HELD -> ErrorCode.SEAT_HELD;
                case RESERVED -> ErrorCode.SEAT_ALREADY_RESERVED;
                case EXPIRED, RELEASED, CANCELED -> ErrorCode.SCREENING_SEATS_NOT_READY;
            };
            throw new BusinessException(error);
        }
    }
}
