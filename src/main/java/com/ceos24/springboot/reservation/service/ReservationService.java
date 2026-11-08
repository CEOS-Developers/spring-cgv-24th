package com.ceos24.springboot.reservation.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationSeat;
import com.ceos24.springboot.reservation.dto.ReservationResponse;
import com.ceos24.springboot.reservation.dto.ReservationCreateRequest;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.repository.ReservationSeatRepository;
import com.ceos24.springboot.theater.domain.Seat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationExpirationService expirationService;
    private final ReservationCreateService createService;
    private final ReservationQueryService queryService;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    // 외부 트랜잭션을 중단해도 커넥션은 반환되지 않으므로, 트랜잭션 내 호출 자체를 차단한다.
    // 만료 트랜잭션이 커밋되고 커넥션을 반환한 뒤 예매 트랜잭션을 시작한다.
    @Transactional(propagation = Propagation.NEVER)
    public ReservationResponse createReservation(Long userId, ReservationCreateRequest request) {
        validateSelection(request);
        expirationService.releaseExpiredSeats(request.screeningId(), request.seatIds());
        return createService.createReservation(userId, request);
    }

    @Transactional(propagation = Propagation.NEVER)
    public ReservationResponse getReservation(Long userId, Long reservationId) {
        expirationService.releaseExpiredReservationsForUser(userId);
        return queryService.getReservation(userId, reservationId);
    }

    @Transactional(propagation = Propagation.NEVER)
    public List<ReservationResponse> getMyReservations(Long userId) {
        expirationService.releaseExpiredReservationsForUser(userId);
        return queryService.getMyReservations(userId);
    }

    @Transactional
    public ReservationResponse cancelReservation(
            Long userId,
            Long reservationId
    ){

        // 로그인한 사용자의 예매인지 확인
        Reservation reservation =
                reservationRepository.findMyReservationWithLock(reservationId, userId)
                        .orElseThrow(() -> new GlobalException(ErrorCode.RESERVATION_NOT_FOUND));

        // 예매 소유권 검사
        if (!reservation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException(
                    "본인의 예매만 취소할 수 있습니다."
            );
        }

        // 취소하기 전 예매 좌석 조회
        List<ReservationSeat> reservationSeats =
                reservationSeatRepository.findAllByReservation_ReservationId(reservationId);

        // ReservationSeat -> Seat 변환
        List<Seat> seats =
                reservationSeats.stream()
                        .map(ReservationSeat::getSeat)
                        .toList();

        // 예매 상태 변경
        reservation.cancel();

        // 좌석 점유 해제
        reservationSeatRepository
                .deleteAllByReservation_ReservationId(reservationId);

        // 취소된 예매 정보 + 기존 좌석 정보 반환
        return ReservationResponse.from(
                reservation,
                seats
        );
    }

    private void validateSelection(ReservationCreateRequest request) {
        if (request == null || request.screeningId() == null || request.screeningId() <= 0
                || request.seatIds() == null || request.seatIds().isEmpty()
                || request.seatIds().stream().anyMatch(id -> id == null || id <= 0)
                || request.seatIds().stream().distinct().count() != request.seatIds().size()
                || request.childCount() == null || request.childCount() < 0
                || request.youthCount() == null || request.youthCount() < 0
                || request.adultCount() == null || request.adultCount() < 0
                || request.seniorCount() == null || request.seniorCount() < 0
                || (long) request.childCount() + request.youthCount() + request.adultCount()
                   + request.seniorCount() != request.seatIds().size()) {
            throw new GlobalException(ErrorCode.INVALID_RESERVATION_REQUEST);
        }
    }

}
