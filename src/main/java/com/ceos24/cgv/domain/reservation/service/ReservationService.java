package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.payment.dto.response.PaymentReadyResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    @Transactional
    public Long create(Long userId, ReservationCreateRequest request) {
        if (request.seatIds().size() != request.seatIds().stream().distinct().count()) {
            throw new GeneralException(ReservationErrorStatus.DUPLICATE_SEAT_REQUEST);
        }

        Instant now = Instant.now(clock);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        Schedule schedule = scheduleRepository.findById(request.scheduleId())
                .orElseThrow(() -> new GeneralException(ScheduleErrorStatus.SCHEDULE_NOT_FOUND));

        if (!schedule.isBeforeStart(LocalDateTime.now(clock))) {
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

        int totalPrice = schedule.getPrice() * seats.size();
        String seatSummary = buildSeatSummary(seats);
        // releaseExpiredHolds의 벌크 쿼리가 영속성 컨텍스트를 비우므로 지연 로딩이 필요한 값은 먼저 읽어 둔다
        String orderName = schedule.getMovie().getTitle() + " " + seats.size() + "매";

        List<ReservationSeat> occupied = reservationSeatRepository
                .findByScheduleIdAndSeatIdIn(request.scheduleId(), request.seatIds());
        List<Long> expiredIds = occupied.stream()
                .map(ReservationSeat::getReservation)
                .filter(reservation -> reservation.isHoldExpired(now))
                .map(Reservation::getId)
                .distinct()
                .toList();
        boolean stillOccupied = occupied.stream().anyMatch(rs -> !expiredIds.contains(rs.getReservation().getId()));
        if (stillOccupied) {
            throw new GeneralException(ReservationErrorStatus.SEAT_ALREADY_RESERVED);
        }
        releaseExpiredHolds(expiredIds, now);

        Reservation reservation = reservationRepository.save(
                Reservation.create(user, schedule, totalPrice, seatSummary, now)
        );

        List<ReservationSeat> reservationSeats = seats.stream()
                .sorted(Comparator.comparing(Seat::getId))
                .map(seat -> ReservationSeat.create(reservation, seat))
                .toList();
        try {
            reservationSeatRepository.saveAllAndFlush(reservationSeats);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            throw new GeneralException(ReservationErrorStatus.SEAT_ALREADY_RESERVED);
        }

        paymentRepository.save(Payment.forReservation(reservation, orderName));

        return reservation.getId();
    }

    @Transactional
    public int releaseExpiredHolds(List<Long> reservationIds, Instant now) {
        if (reservationIds.isEmpty()) {
            return 0;
        }
        int expired = reservationRepository.expireHolds(reservationIds, now);
        reservationSeatRepository.deleteByExpiredReservationIds(reservationIds);
        paymentRepository.expireByExpiredReservationIds(reservationIds);
        return expired;
    }

    @Transactional
    public void expireOverdueHolds() {
        Instant now = Instant.now(clock);
        int expired = releaseExpiredHolds(reservationRepository.findExpiredHoldIds(now), now);
        if (expired > 0) {
            log.info("만료된 좌석 선점 {}건을 해제했습니다.", expired);
        }
    }

    public PaymentReadyResponse getPaymentInfo(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new GeneralException(ReservationErrorStatus.RESERVATION_NOT_FOUND));

        if (!reservation.isOwnedBy(userId)) {
            throw new GeneralException(ReservationErrorStatus.NOT_RESERVATION_OWNER);
        }

        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT
                || reservation.isHoldExpired(Instant.now(clock))) {
            throw new GeneralException(ReservationErrorStatus.RESERVATION_NOT_PENDING_PAYMENT);
        }

        Payment payment = paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new GeneralException(PaymentErrorStatus.PAYMENT_NOT_FOUND));
        return PaymentReadyResponse.from(payment);
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

        if (reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new GeneralException(ReservationErrorStatus.ALREADY_CANCELLED);
        }

        if (reservation.getStatus() == ReservationStatus.PAYING) {
            throw new GeneralException(ReservationErrorStatus.RESERVATION_PAYMENT_IN_PROGRESS);
        }

        boolean pendingPayment = reservation.getStatus() == ReservationStatus.PENDING_PAYMENT;
        if (!pendingPayment && paymentRepository.findByReservationId(reservationId).isPresent()) {
            throw new GeneralException(ReservationErrorStatus.PAYMENT_CANCEL_REQUIRED);
        }

        validateCancelable(reservation);
        reservation.cancel();
        reservationSeatRepository.deleteByReservationId(reservationId);
        if (pendingPayment) {
            paymentRepository.findByReservationId(reservationId)
                    .filter(payment -> payment.getStatus() == PaymentStatus.READY)
                    .ifPresent(Payment::expire);
        }
    }

    @Transactional
    public void startPaying(Long reservationId) {
        if (reservationRepository.startPaying(reservationId, Instant.now(clock)) == 0) {
            throw new GeneralException(ReservationErrorStatus.RESERVATION_HOLD_EXPIRED);
        }
    }

    @Transactional
    public void confirmPaid(Reservation reservation) {
        reservation.confirmPayment();
    }

    @Transactional
    public void releaseUnpaid(Reservation reservation) {
        release(reservation, ReservationStatus.PAYING);
    }

    @Transactional
    public void releaseCanceled(Reservation reservation) {
        release(reservation, ReservationStatus.RESERVED);
    }

    public void validateCancelable(Reservation reservation) {
        if (!reservation.getSchedule().isBeforeCancelDeadline(LocalDateTime.now(clock))) {
            throw new GeneralException(ReservationErrorStatus.CANCEL_DEADLINE_PASSED);
        }
    }

    private void release(Reservation reservation, ReservationStatus expected) {
        if (reservation.getStatus() != expected) {
            return;
        }
        reservation.cancel();
        reservationSeatRepository.deleteByReservationId(reservation.getId());
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
