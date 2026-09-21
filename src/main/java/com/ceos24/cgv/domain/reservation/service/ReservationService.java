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
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
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

    /**
     * 영화 예매
     */
    @Transactional
    public ReservationResponse createReservation(
            Long userId,
            ReservationCreateRequest request
    ) {
        // 요청한 상영 일정과 좌석 목록을 검증
        validateReservationRequest(request);

        User user = getUserOrThrow(userId);

        // 예매할 상영 일정 체크
        Screening screening =
                getScreeningOrThrow(request.screeningId());

        // 이미 시작된 상영 일정인지?
        validateScreeningNotStarted(screening);


        List<Long> seatIds = request.seatIds()
                .stream()
                .sorted()
                .toList();

        // 동시 예매를 방지하기 위해 좌석 -> 비관적 락 처리
        List<Seat> seats =
                seatRepository.findAllByIdForUpdate(seatIds);


        validateAllSeatsExist(seatIds, seats);

        // 좌석이 해당 상영 일정의 상영관에 속하는지
        validateSeatsBelongToScreen(screening, seats);

        // 해당 상영 일정에서 이미 예매된 좌석인지
        validateSeatsNotReserved(
                screening.getId(),
                seatIds
        );


        Reservation reservation =
                Reservation.create(user, screening);

        // 상영관 타입의 기본 가격을 좌석 가격으로
        Integer seatPrice = screening.getScreen()
                .getScreenType()
                .getBasePrice();

        // 요청한 좌석을 예매에 추가하고 총 가격을 계산
        for (Seat seat : seats) {
            ReservationSeat reservationSeat =
                    ReservationSeat.create(seat);

            reservation.addReservationSeat(
                    reservationSeat,
                    seatPrice
            );
        }

        // 예매와 예매 좌석을 함께 저장한다.
        Reservation savedReservation =
                reservationRepository.save(reservation);

        // 저장된 예매를 응답 DTO로 변환한다.
        return ReservationResponse.from(savedReservation);
    }

    /**
     * 영화 예매 취소
     */
    @Transactional
    public void cancelReservation(
            Long userId,
            Long reservationId
    ) {

        User user = getUserOrThrow(userId);

        // 중복 취소를 막기 위해 예매를 비관적 락 처리
        Reservation reservation =
                reservationRepository
                        .findByIdForUpdate(reservationId)
                        .orElseThrow(() ->
                                new ReservationException(
                                        ReservationErrorCode
                                                .RESERVATION_NOT_FOUND
                                )
                        );


        validateReservationOwner(
                reservation,
                user
        );

        // 이미 취소된 예매인지
        validateNotAlreadyCanceled(reservation);

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
            Long screeningId,
            List<Long> seatIds
    ) {
        List<Long> reservedSeatIds =
                reservationSeatRepository
                        .findReservedSeatIds(
                                screeningId,
                                seatIds,
                                ReservationStatus.RESERVED
                        );

        if (!reservedSeatIds.isEmpty()) {
            throw new ReservationException(
                    ReservationErrorCode
                            .SEAT_ALREADY_RESERVED
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