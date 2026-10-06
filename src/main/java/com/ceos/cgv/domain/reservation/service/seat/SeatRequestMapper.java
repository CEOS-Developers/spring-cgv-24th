package com.ceos.cgv.domain.reservation.service.seat;

import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.value.SeatCoordinate;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import java.util.List;

public final class SeatRequestMapper {
    private SeatRequestMapper() {
    }

    public static List<SeatCoordinate> toCoordinates(List<ReservedSeatRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return requests.stream().map(request -> {
            if (request == null || request.seatNumber() == null) {
                throw new BusinessException(ErrorCode.INVALID_SEAT);
            }
            return new SeatCoordinate(request.seatRow(), request.seatNumber());
        }).toList();
    }
}
