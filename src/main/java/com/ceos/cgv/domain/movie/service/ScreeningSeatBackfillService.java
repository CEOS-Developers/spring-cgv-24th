package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.entity.ReservedSeat;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservedSeatRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScreeningSeatBackfillService {
    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatRepository screeningSeatRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final Clock seatHoldClock;

    @Transactional
    public BackfillResult backfill(Long screeningId) {
        Screening screening = screeningRepository.findByIdWithLock(screeningId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));
        Screen screen = screening.getScreen();
        if (screen.getRowCount() < 1 || screen.getRowCount() > 26
                || screen.getSeatsPerRow() < 1) {
            throw conflict();
        }

        List<ScreeningSeat> seats = screeningSeatRepository.findAllByScreening_Id(screeningId);
        int seatsCreated = 0;
        if (seats.isEmpty()) {
            seats = new ArrayList<>();
            for (int row = 0; row < screen.getRowCount(); row++) {
                String seatRow = String.valueOf((char) ('A' + row));
                for (int number = 1; number <= screen.getSeatsPerRow(); number++) {
                    seats.add(new ScreeningSeat(screening, seatRow, number));
                }
            }
            screeningSeatRepository.saveAll(seats);
            seatsCreated = seats.size();
        }
        if (seats.size() != (long) screen.getRowCount() * screen.getSeatsPerRow()) {
            throw conflict();
        }
        Map<SeatCoordinate, ScreeningSeat> seatsByCoordinate = seats.stream()
                .collect(Collectors.toMap(
                        seat -> new SeatCoordinate(seat.getSeatRow(), seat.getSeatNumber()),
                        Function.identity(), (first, duplicate) -> {
                            throw conflict();
                        }));
        for (int row = 0; row < screen.getRowCount(); row++) {
            String seatRow = String.valueOf((char) ('A' + row));
            for (int number = 1; number <= screen.getSeatsPerRow(); number++) {
                if (!seatsByCoordinate.containsKey(new SeatCoordinate(seatRow, number))) {
                    throw conflict();
                }
            }
        }

        List<ReservedSeat> histories = reservedSeatRepository.findAllByReservation_Screening_Id(screeningId);
        Map<SeatCoordinate, Reservation> activeReservations = new HashMap<>();
        Set<HistoryCoordinate> seenHistoryCoordinates = new HashSet<>();
        Instant now = seatHoldClock.instant();
        for (ReservedSeat history : histories) {
            SeatCoordinate coordinate = new SeatCoordinate(history.getSeatRow(), history.getSeatNumber());
            ScreeningSeat seat = seatsByCoordinate.get(coordinate);
            if (seat == null || !seenHistoryCoordinates.add(
                    new HistoryCoordinate(history.getReservation().getId(), coordinate))) {
                throw conflict();
            }
            if (history.getScreeningSeat() != null
                    && !history.getScreeningSeat().getId().equals(seat.getId())) {
                throw conflict();
            }
            Reservation reservation = history.getReservation();
            boolean active = switch (reservation.getStatus()) {
                case RESERVED -> true;
                case HELD -> {
                    if (reservation.getExpiresAt() == null) {
                        throw conflict();
                    }
                    yield !reservation.isExpiredAt(now);
                }
                case CANCELED, EXPIRED, RELEASED -> false;
            };
            if (active) {
                if (activeReservations.putIfAbsent(coordinate, reservation) != null) {
                    throw conflict();
                }
            }
        }

        int historiesLinked = 0;
        for (ReservedSeat history : histories) {
            if (history.getScreeningSeat() == null) {
                history.linkScreeningSeat(seatsByCoordinate.get(
                        new SeatCoordinate(history.getSeatRow(), history.getSeatNumber())));
                historiesLinked++;
            }
        }
        int occupantsLinked = 0;
        for (Map.Entry<SeatCoordinate, ScreeningSeat> entry : seatsByCoordinate.entrySet()) {
            Reservation expected = activeReservations.get(entry.getKey());
            Reservation current = entry.getValue().getCurrentReservation();
            if (current != null && (expected == null || !current.getId().equals(expected.getId()))) {
                throw conflict();
            }
            if (current == null && expected != null) {
                entry.getValue().restoreCurrentReservation(expected);
                occupantsLinked++;
            }
        }
        return new BackfillResult(seatsCreated, historiesLinked, occupantsLinked);
    }

    private static BusinessException conflict() {
        return new BusinessException(ErrorCode.SEAT_MIGRATION_CONFLICT);
    }

    public record BackfillResult(int seatsCreated, int historiesLinked, int occupantsLinked) {
    }

    private record HistoryCoordinate(Long reservationId, SeatCoordinate coordinate) {
    }
}
