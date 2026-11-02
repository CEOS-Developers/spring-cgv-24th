package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.reservation.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos24.cgv.domain.reservation.exception.ReservationErrorCode;
import com.ceos24.cgv.domain.reservation.exception.ReservationException;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.entity.ScreeningSeat;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.screening.repository.ScreeningSeatRepository;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.exception.UserErrorCode;
import com.ceos24.cgv.domain.user.exception.UserException;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final ScreeningSeatRepository screeningSeatRepository;

    /**
     * 영화 예매
     */
    /**
     * 영화 예매
     */
    @Transactional
    public ReservationResponse createReservation(
            Long userId,
            ReservationCreateRequest request
    ) {
        validateReservationRequest(request);

        User user = getUserOrThrow(userId);
        Screening screening = getScreeningOrThrow(request.screeningId());

        validateScreeningNotStarted(screening);

        List<Long> seatIds = request.seatIds().stream()
                .sorted()
                .toList();

        // 물리 좌석 대신 상영 일정별 좌석을 잠가
        // 다른 상영 일정의 동일 좌석 예매와 경합하지 않도록 한다.
        List<ScreeningSeat> screeningSeats =
                screeningSeatRepository.findAllForUpdate(
                        screening.getId(),
                        seatIds
                );

        List<Seat> seats = screeningSeats.stream()
                .map(ScreeningSeat::getSeat)
                .toList();

        // 요청한 모든 좌석이 해당 상영 일정에 등록되어 있는지 확인한다.
        validateAllSeatsExist(seatIds, seats);
        validateSeatsBelongToScreen(screening, seats);

        // 락 대기 중 상영 시작 시간이 지났을 수 있으므로 다시 확인한다.
        validateScreeningNotStarted(screening);

        // 잠금을 획득한 최신 점유 상태를 기준으로 중복 예매를 검사한다.
        validateSeatsNotReserved(screeningSeats);

        // 점유 변경과 예매 저장은 같은 트랜잭션에서 처리한다.
        // 이후 저장에 실패하면 점유 변경도 함께 롤백된다.
        screeningSeats.forEach(ScreeningSeat::reserve);

        Reservation reservation = Reservation.create(user, screening);

        Integer seatPrice = screening.getScreen()
                .getScreenType()
                .getBasePrice();

        for (Seat seat : seats) {
            ReservationSeat reservationSeat = ReservationSeat.create(seat);

            reservation.addReservationSeat(
                    reservationSeat,
                    seatPrice
            );
        }

        Reservation savedReservation =
                reservationRepository.save(reservation);

        return ReservationResponse.from(savedReservation);
    }
    /**
     * 영화 예매 취소
     */
    /**
     * 영화 예매 취소
     */
    @Transactional
    public void cancelReservation(
            Long userId,
            Long reservationId
    ) {
        User user = getUserOrThrow(userId);

        // 동일 예매의 취소 요청을 직렬화한다.
        Reservation reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new ReservationException(
                                ReservationErrorCode.RESERVATION_NOT_FOUND
                        )
                );

        validateReservationOwner(reservation, user);

        // 이미 취소된 예매가 재예매된 좌석을 해제하지 않도록
        // 좌석 점유 해제 전에 예매 상태부터 확인한다.
        validateNotAlreadyCanceled(reservation);

        List<Long> seatIds = reservation.getReservationSeats().stream()
                .map(reservationSeat -> reservationSeat.getSeat().getId())
                .sorted()
                .toList();

        if (seatIds.isEmpty()) {
            throw new IllegalStateException(
                    "예매에 연결된 좌석 이력이 없습니다."
            );
        }

        // 예매 생성과 동일한 상영 일정별 좌석을 잠근다.
        List<ScreeningSeat> screeningSeats =
                screeningSeatRepository.findAllForUpdate(
                        reservation.getScreening().getId(),
                        seatIds
                );

        if (screeningSeats.size() != seatIds.size()) {
            throw new IllegalStateException(
                    "예매 좌석 이력과 상영 일정별 좌석 정보가 일치하지 않습니다."
            );
        }

        // 좌석 점유만 해제하고 ReservationSeat 이력은 유지한다.
        screeningSeats.forEach(ScreeningSeat::release);

        reservation.cancel();
    }

    private void validateReservationRequest(
            ReservationCreateRequest request
    ) {
        if (request == null
                || request.screeningId() == null) {
            throw new ReservationException(
                    ReservationErrorCode.SCREENING_NOT_FOUND
            );
        }

        if (request.seatIds() == null
                || request.seatIds().isEmpty()) {
            throw new ReservationException(
                    ReservationErrorCode.EMPTY_SEAT_LIST
            );
        }

        Set<Long> uniqueSeatIds = new HashSet<>();

        for (Long seatId : request.seatIds()) {
            if (seatId == null) {
                throw new ReservationException(
                        ReservationErrorCode.SEAT_NOT_FOUND
                );
            }

            if (!uniqueSeatIds.add(seatId)) {
                throw new ReservationException(
                        ReservationErrorCode.DUPLICATE_SEAT
                );
            }
        }
    }

    private void validateAllSeatsExist(
            List<Long> seatIds,
            List<Seat> seats
    ) {
        if (seatIds.size() != seats.size()) {
            throw new ReservationException(
                    ReservationErrorCode.SEAT_NOT_FOUND
            );
        }
    }

    private void validateSeatsBelongToScreen(
            Screening screening,
            List<Seat> seats
    ) {
        Long screenId = screening.getScreen().getId();

        boolean invalidSeatExists = seats.stream()
                .anyMatch(seat ->
                        !seat.getScreen()
                                .getId()
                                .equals(screenId)
                );

        if (invalidSeatExists) {
            throw new ReservationException(
                    ReservationErrorCode.INVALID_SEAT
            );
        }
    }

    private void validateSeatsNotReserved(
            List<ScreeningSeat> screeningSeats
    ) {
        boolean reservedSeatExists = screeningSeats.stream()
                .anyMatch(ScreeningSeat::isReserved);

        if (reservedSeatExists) {
            throw new ReservationException(
                    ReservationErrorCode.SEAT_ALREADY_RESERVED
            );
        }
    }

    private void validateScreeningNotStarted(
            Screening screening
    ) {
        if (!screening.getStartTime()
                .isAfter(LocalDateTime.now())) {
            throw new ReservationException(
                    ReservationErrorCode
                            .SCREENING_ALREADY_STARTED
            );
        }
    }

    private void validateReservationOwner(
            Reservation reservation,
            User user
    ) {
        if (!reservation.getUser()
                .getId()
                .equals(user.getId())) {
            throw new ReservationException(
                    ReservationErrorCode
                            .RESERVATION_ACCESS_DENIED
            );
        }
    }

    private void validateNotAlreadyCanceled(
            Reservation reservation
    ) {
        if (reservation.isCanceled()) {
            throw new ReservationException(
                    ReservationErrorCode
                            .RESERVATION_ALREADY_CANCELED
            );
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserException(
                                UserErrorCode.USER_NOT_FOUND
                        )
                );
    }

    private Screening getScreeningOrThrow(
            Long screeningId
    ) {
        return screeningRepository.findById(screeningId)
                .orElseThrow(() ->
                        new ReservationException(
                                ReservationErrorCode
                                        .SCREENING_NOT_FOUND
                        )
                );
    }
}