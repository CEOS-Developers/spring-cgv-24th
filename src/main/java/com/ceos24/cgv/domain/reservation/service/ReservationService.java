package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.cinema.entity.Seat;
import com.ceos24.cgv.domain.cinema.repository.SeatRepository;
import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ScreeningRepository screeningRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;

    // 예매 등록
    @Transactional
    public Long createReservation(Long userId, ReservationCreateRequest request) {

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

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
                reservationSeatRepository.findSeatIdsByScreeningIdAndStatus(
                        screening.getId(),
                        ReservationStatus.CONFIRMED
                )
        );

        // 요청 좌석 중 하나라도 포함되어 있으면 예매 중단
        boolean containsReservedSeat = distinctSeatIds.stream()
                .anyMatch(alreadyReservedSeatIds::contains);

        if (containsReservedSeat) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_RESERVED);
        }

        Reservation reservation = reservationRepository.save(
                Reservation.create(screening, user)
        );

        List<ReservationSeat> reservationSeats = seats.stream()
                .map(seat -> ReservationSeat.create(seat, reservation))
                .toList();
        reservationSeatRepository.saveAll(reservationSeats);

        return reservation.getId();
    }

    public List<ReservationResponse> getReservations(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return reservationRepository.findAllByUserIdOrderByReservedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ReservationResponse getReservation(Long userId, Long reservationId) {
        return toResponse(getOwnedReservation(userId, reservationId));
    }

    @Transactional
    public void cancelReservation(Long userId, Long reservationId) {
        Reservation reservation = getOwnedReservation(userId, reservationId);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
        }

        reservation.cancel();
    }

    // 자신의 예매만 조회·취소하도록 검사
    private Reservation getOwnedReservation(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
        }

        return reservation;
    }

    private ReservationResponse toResponse(Reservation reservation) {
        List<ReservationSeat> reservationSeats = reservationSeatRepository
                .findAllByReservationIdOrderBySeatRowNumberAscSeatColumnNumberAsc(reservation.getId());

        return ReservationResponse.from(reservation, reservationSeats);
    }
}
