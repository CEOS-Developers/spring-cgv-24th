package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.config.SeatHoldProperties;
import com.ceos.cgv.domain.reservation.service.result.HoldCreationResult;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.domain.reservation.dto.SeatHoldResponse;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.repository.ReservedSeatRepository;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatHoldCreationService {
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatRepository screeningSeatRepository;
    private final MovieRepository movieRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final ScreeningSeatLockService seatLockService;
    private final SeatHoldProperties properties;
    private final Clock seatHoldClock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldCreationResult create(
            Long userId, UUID requestKey, SeatHoldCreateRequest request) {
        if (request.seats() == null || request.seats().isEmpty()
                || request.seats().size() > properties.maxSeats()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Set<SeatCoordinate> coordinates = ReservationSeatPolicy.coordinates(request.seats());
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Instant now = seatHoldClock.instant();

        var existing = reservationRepository.findByUser_IdAndRequestKey(userId, requestKey.toString());
        if (existing.isPresent()) {
            Reservation reservation = existing.get();
            Set<SeatCoordinate> original = reservation.getReservedSeats().stream()
                    .map(seat -> new SeatCoordinate(seat.getSeatRow(), seat.getSeatNumber()))
                    .collect(Collectors.toSet());
            if (!reservation.getScreening().getId().equals(request.screeningId())
                    || !original.equals(coordinates)) {
                throw new BusinessException(ErrorCode.HOLD_REQUEST_CONFLICT);
            }
            if (reservation.isExpiredAt(now)) {
                throw new ExpiredHoldEncountered(reservation.getId(), true);
            }
            if (reservation.getStatus() != ReservationStatus.HELD
                    && reservation.getStatus() != ReservationStatus.RESERVED) {
                throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
            }
            return new HoldCreationResult(SeatHoldResponse.from(reservation), false);
        }

        if (reservationRepository.countActiveHolds(userId, ReservationStatus.HELD, now)
                >= properties.maxActive()) {
            throw new BusinessException(ErrorCode.HOLD_LIMIT_REACHED);
        }
        Screening screening = screeningRepository.findById(request.screeningId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));
        Movie movie = movieRepository.findByIdForShare(screening.getMovie().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
        movie.ensurePublic();
        Screen screen = screening.getScreen();
        ReservationSeatPolicy.validateBounds(screen, coordinates);
        ReservationSeatPolicy.validateInventory(screen,
                screeningSeatRepository.countByScreening_Id(screening.getId()), false);
        List<ScreeningSeat> seats = seatLockService.lockSeats(screening.getId(), coordinates);
        now = seatHoldClock.instant();
        ReservationSeatPolicy.ensureAvailable(seats, now);
        if (reservedSeatRepository.existsReservedByScreeningIdAndCoordinates(screening.getId(), coordinates)) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_RESERVED);
        }
        Reservation hold = Reservation.hold(user, screening, requestKey,
                seatHoldClock.instant().plus(properties.duration()));
        Map<SeatCoordinate, ScreeningSeat> byCoordinate = seats.stream()
                .collect(Collectors.toMap(
                        seat -> new SeatCoordinate(seat.getSeatRow(), seat.getSeatNumber()), Function.identity()));
        for (ReservedSeatRequest requested : request.seats()) {
            hold.addSeat(requested.seatRow(), requested.seatNumber(), byCoordinate.get(
                    new SeatCoordinate(requested.seatRow(), requested.seatNumber())));
        }
        reservationRepository.saveAndFlush(hold);
        seats.forEach(seat -> seat.occupy(hold));
        return new HoldCreationResult(SeatHoldResponse.from(hold), true);
    }

}
