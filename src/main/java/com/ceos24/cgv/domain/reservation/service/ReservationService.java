package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.dto.response.SeatInfoResponse;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.exception.ReservationErrorStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.schedule.entity.Schedule;
import com.ceos24.cgv.domain.schedule.exception.ScheduleErrorStatus;
import com.ceos24.cgv.domain.schedule.repository.ScheduleRepository;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.exception.UserErrorStatus;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long create(Long userId, ReservationCreateRequest request) {
        if (request.seatIds().size() != request.seatIds().stream().distinct().count()) {
            throw new GeneralException(ReservationErrorStatus.DUPLICATE_SEAT_REQUEST);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        Schedule schedule = scheduleRepository.findById(request.scheduleId())
                .orElseThrow(() -> new GeneralException(ScheduleErrorStatus.SCHEDULE_NOT_FOUND));

        if (!schedule.isBeforeStart(LocalDateTime.now())) {
            throw new GeneralException(ReservationErrorStatus.RESERVATION_CLOSED);
        }

        List<Seat> seats = seatRepository.findAllById(request.seatIds());
        if (seats.size() != request.seatIds().size()) {
            throw new GeneralException(ReservationErrorStatus.SEAT_NOT_FOUND);
        }

        boolean allSeatsInScreen = seats.stream()
                .allMatch(seat -> seat.getScreen().getId().equals(schedule.getScreen().getId()));
        if (!allSeatsInScreen) {
            throw new GeneralException(ReservationErrorStatus.SEAT_NOT_IN_SCREEN);
        }

        List<ReservationSeat> alreadyReserved = reservationSeatRepository
                .findByScheduleIdAndSeatIdIn(request.scheduleId(), request.seatIds());
        if (!alreadyReserved.isEmpty()) {
            throw new GeneralException(ReservationErrorStatus.SEAT_ALREADY_RESERVED);
        }

        int totalPrice = schedule.getPrice() * seats.size();
        String seatSummary = buildSeatSummary(seats);

        Reservation reservation = reservationRepository.save(
                Reservation.create(user, schedule, totalPrice, seatSummary)
        );

        for (Seat seat : seats) {
            reservationSeatRepository.save(ReservationSeat.create(reservation, seat));
        }

        return reservation.getId();
    }

    public ReservationResponse findById(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findByIdWithDetails(reservationId)
                .orElseThrow(() -> new GeneralException(ReservationErrorStatus.RESERVATION_NOT_FOUND));

        if (!reservation.isOwnedBy(userId)) {
            throw new GeneralException(ReservationErrorStatus.NOT_RESERVATION_OWNER);
        }

        List<SeatInfoResponse> seats = reservationSeatRepository.findByReservationIdWithSeat(reservationId).stream()
                .map(rs -> SeatInfoResponse.from(rs.getSeat()))
                .toList();

        return ReservationResponse.of(reservation, seats);
    }

    public List<ReservationResponse> findByUserId(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        List<Reservation> reservations = reservationRepository.findByUserIdWithDetailsOrderByReservedAtDesc(userId);
        List<Long> reservationIds = reservations.stream().map(Reservation::getId).toList();

        Map<Long, List<SeatInfoResponse>> seatsByReservationId = reservationSeatRepository
                .findByReservationIdInWithSeat(reservationIds).stream()
                .collect(Collectors.groupingBy(
                        rs -> rs.getReservation().getId(),
                        Collectors.mapping(rs -> SeatInfoResponse.from(rs.getSeat()), Collectors.toList())
                ));

        return reservations.stream()
                .map(reservation -> ReservationResponse.of(
                        reservation,
                        seatsByReservationId.getOrDefault(reservation.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public void cancel(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new GeneralException(ReservationErrorStatus.RESERVATION_NOT_FOUND));

        if (!reservation.isOwnedBy(userId)) {
            throw new GeneralException(ReservationErrorStatus.NOT_RESERVATION_OWNER);
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new GeneralException(ReservationErrorStatus.ALREADY_CANCELLED);
        }

        if (!reservation.getSchedule().isBeforeCancelDeadline(LocalDateTime.now())) {
            throw new GeneralException(ReservationErrorStatus.CANCEL_DEADLINE_PASSED);
        }

        reservation.cancel();
        reservationSeatRepository.deleteByReservationId(reservationId);
    }

    private String buildSeatSummary(List<Seat> seats) {
        return seats.stream()
                .map(seat -> rowNumToLabel(seat.getRowNum()) + seat.getColNum())
                .collect(Collectors.joining(", "));
    }

    // 1→A, 2→B ... 형태로 변환 (CGV 실제 좌석 표기 방식)
    private String rowNumToLabel(Integer rowNum) {
        return String.valueOf((char) ('A' + rowNum - 1));
    }
}
