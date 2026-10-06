package com.ceos.cgv.domain.reservation.policy;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.reservation.value.SeatCoordinate;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class ReservationSeatPolicy {
    private ReservationSeatPolicy() {
    }

    public static Set<SeatCoordinate> uniqueCoordinates(Collection<SeatCoordinate> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Set<SeatCoordinate> coordinates = new HashSet<>();
        for (SeatCoordinate seat : seats) {
            if (seat == null || seat.row() == null || !seat.row().matches("[A-Z]")
                    || seat.number() < 1) {
                throw new BusinessException(ErrorCode.INVALID_SEAT);
            }
            if (!coordinates.add(seat)) {
                throw new BusinessException(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
            }
        }
        return coordinates;
    }

    public static void validateBounds(Screen screen, Collection<SeatCoordinate> coordinates) {
        for (SeatCoordinate coordinate : coordinates) {
            int row = coordinate.row().charAt(0) - 'A' + 1;
            if (row > screen.getRowCount() || coordinate.number() > screen.getSeatsPerRow()) {
                throw new BusinessException(ErrorCode.INVALID_SEAT);
            }
        }
    }

    public static boolean validateInventory(Screen screen, long seatCount, boolean allowLegacy) {
        if (seatCount == 0 && allowLegacy) {
            return true;
        }
        if (seatCount != (long) screen.getRowCount() * screen.getSeatsPerRow()) {
            throw new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY);
        }
        return false;
    }

}
