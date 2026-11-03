package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.cinema.entity.Seat;
import com.ceos24.cgv.domain.cinema.repository.SeatRepository;
import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationCreateResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.entity.PaymentOperationStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentOperationRepository;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReservationService {

    private static final int HOLD_MINUTES = 10;

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ScreeningRepository screeningRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final ReservationHoldService holdService;

    // 결제 대기 예매·좌석 점유·확정 금액을 한 트랜잭션에 저장한다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationCreateResponse createReservation(String username, ReservationCreateRequest request) {

        holdService.expireForScreening(request.screeningId());

        // 사용자 조회
        UserEntity userEntity = findActiveLocalUser(username);

        // 상영 정보 조회
        Screening screening = screeningRepository.findById(request.screeningId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));

        // 상영 시작 여부 확인
        if (!LocalDateTime.now().isBefore(screening.getStartsAt())) {
            throw new BusinessException(ErrorCode.SCREENING_ALREADY_STARTED);
        }

        // 요청 좌석 ID 중복 확인
        List<Long> distinctSeatIds = request.seatIds().stream()
                .distinct() // distinct()로 중복을 제거한 뒤 원래 목록 크기와 비교
                .sorted()
                .toList();

        if (distinctSeatIds.size() != request.seatIds().size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SEAT_REQUEST);
        }

        // 같은 좌석을 동시에 예매하지 못하도록 선택한 좌석 행을 잠금
        List<Seat> seats = seatRepository.findAllByIdInForUpdate(distinctSeatIds);

        // 잠금을 기다리는 동안 상영이 시작됐을 수도 있다.
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(screening.getStartsAt())) {
            throw new BusinessException(ErrorCode.SCREENING_ALREADY_STARTED);
        }

        if (seats.size() != distinctSeatIds.size()) {
            throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
        }

        // 모든 좌석이 상영 회차의 상영관에 속하는지 검사
        Long auditoriumId = screening.getAuditorium().getId();
        boolean containsOtherAuditoriumSeat = seats.stream()
                .anyMatch(seat -> !seat.getAuditorium().getId().equals(auditoriumId));

        if (containsOtherAuditoriumSeat) {
            throw new BusinessException(ErrorCode.SEAT_NOT_IN_SCREENING_AUDITORIUM);
        }

        // 이미 예매된 좌석인지 검사
        Set<Long> alreadyReservedSeatIds = new HashSet<>(
                reservationSeatRepository.findSeatIdsByScreeningIdAndStatuses(
                        screening.getId(),
                        ReservationStatus.seatHoldingStatuses()
                )
        );

        // 요청 좌석 중 하나라도 포함되어 있으면 예매 중단
        boolean containsReservedSeat = distinctSeatIds.stream()
                .anyMatch(alreadyReservedSeatIds::contains);

        if (containsReservedSeat) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_RESERVED);
        }

        long totalAmount;
        try {
            totalAmount = Math.multiplyExact((long) screening.getPrice(), seats.size());
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_AMOUNT);
        }
        if (totalAmount <= 0) throw new BusinessException(ErrorCode.INVALID_RESERVATION_AMOUNT);
        LocalDateTime expiresAt = now.plusMinutes(HOLD_MINUTES);
        if (expiresAt.isAfter(screening.getStartsAt())) expiresAt = screening.getStartsAt();

        Reservation reservation = reservationRepository.save(
                Reservation.create(screening, userEntity)
        );

        List<ReservationSeat> reservationSeats = seats.stream()
                .map(seat -> ReservationSeat.create(seat, reservation))
                .toList();
        reservationSeatRepository.saveAll(reservationSeats);

        Payment payment = paymentRepository.save(Payment.createForReservation(reservation, totalAmount, expiresAt));
        return ReservationCreateResponse.from(reservation, payment);
    }

    public List<ReservationResponse> getReservations(String username) {
        UserEntity userEntity = findActiveLocalUser(username);

        return reservationRepository
                .findAllByUserEntity_IdOrderByReservedAtDesc(
                        userEntity.getId()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ReservationResponse getReservation(
            String username,
            Long reservationId
    ) {
        UserEntity userEntity = findActiveLocalUser(username);

        return toResponse(
                getOwnedReservation(
                        userEntity.getId(),
                        reservationId
                )
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelReservation(
            String username,
            Long reservationId
    ) {
        UserEntity userEntity = findActiveLocalUser(username);

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        requireOwner(userEntity.getId(), reservation);

        if (reservation.getStatus()
                == ReservationStatus.CANCELLED) {
            throw new BusinessException(
                    ErrorCode.RESERVATION_ALREADY_CANCELLED
            );
        }

        if (reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_EXPIRED);
        }
        // 승인 준비/결과 반영과 같은 예매 → 결제 → 작업 잠금 순서를 따른다.
        Payment payment = paymentRepository.findByReservationIdForUpdate(reservationId).orElse(null);
        if (payment != null) {
            if (!paymentOperationRepository.findAllByPaymentIdAndStatusInForUpdate(payment.getId(),
                    List.of(PaymentOperationStatus.PROCESSING, PaymentOperationStatus.UNKNOWN)).isEmpty()) {
                throw new BusinessException(ErrorCode.PAYMENT_OPERATION_IN_PROGRESS);
            }
            if (payment.getStatus() == PaymentStatus.DONE || payment.getStatus() == PaymentStatus.PARTIAL_CANCELED) {
                throw new BusinessException(ErrorCode.RESERVATION_PAYMENT_CANCEL_REQUIRED);
            }
            if (payment.getStatus() != PaymentStatus.READY || reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
                throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
            }
            payment.abort();
        } else if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        reservation.cancel();
    }

    // 자신의 예매만 조회·취소하도록 검사
    private Reservation getOwnedReservation(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        requireOwner(userId, reservation);
        return reservation;
    }

    private void requireOwner(Long userId, Reservation reservation) {
        if (!reservation.getUserEntity().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }
    }

    private ReservationResponse toResponse(Reservation reservation) {
        List<ReservationSeat> reservationSeats = reservationSeatRepository
                .findAllByReservationIdOrderBySeatRowNumberAscSeatColumnNumberAsc(reservation.getId());

        Payment payment = paymentRepository.findByReservation_Id(reservation.getId()).orElse(null);
        return ReservationResponse.from(reservation, reservationSeats, payment);
    }

    private UserEntity findActiveLocalUser(String username) {
        return userRepository
                .findByUsernameAndIsLockAndIsSocial(
                        username,
                        false,
                        false
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );
    }
}
