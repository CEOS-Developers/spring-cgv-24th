package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ReservationSeatPolicy {
    private ReservationSeatPolicy() {
    }

    static Set<SeatCoordinate> coordinates(List<ReservedSeatRequest> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Set<SeatCoordinate> coordinates = new HashSet<>();
        for (ReservedSeatRequest seat : seats) {
            if (seat == null || seat.seatRow() == null || !seat.seatRow().matches("[A-Z]")
                    || seat.seatNumber() == null || seat.seatNumber() < 1) {
                throw new BusinessException(ErrorCode.INVALID_SEAT);
            }
            if (!coordinates.add(new SeatCoordinate(seat.seatRow(), seat.seatNumber()))) {
                throw new BusinessException(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
            }
        }
        return coordinates;
    }

    static void validateBounds(Screen screen, Collection<SeatCoordinate> coordinates) {
        for (SeatCoordinate coordinate : coordinates) {
            int row = coordinate.row().charAt(0) - 'A' + 1;
            if (row > screen.getRowCount() || coordinate.number() > screen.getSeatsPerRow()) {
                throw new BusinessException(ErrorCode.INVALID_SEAT);
            }
        }
    }

    static boolean validateInventory(Screen screen, long seatCount, boolean allowLegacy) {
        if (seatCount == 0 && allowLegacy) {
            return true;
        }
        if (seatCount != (long) screen.getRowCount() * screen.getSeatsPerRow()) {
            throw new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY);
        }
        return false;
    }

    // 호출자는 먼저 모든 요청 좌석을 정해진 순서로 잠가야 한다.
    static void ensureAvailable(List<ScreeningSeat> lockedSeats, Instant now) {
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
