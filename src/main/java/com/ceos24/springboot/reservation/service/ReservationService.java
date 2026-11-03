package com.ceos24.springboot.reservation.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.reservation.domain.*;
import com.ceos24.springboot.reservation.dto.ReservationCreateRequest;
import com.ceos24.springboot.reservation.dto.ReservationResponse;
import com.ceos24.springboot.reservation.exception.ReservationExpiredException;
import com.ceos24.springboot.reservation.repository.PriceRepository;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.repository.ReservationSeatRepository;
import com.ceos24.springboot.theater.domain.ScreenType;
import com.ceos24.springboot.theater.domain.Screening;
import com.ceos24.springboot.theater.domain.Seat;
import com.ceos24.springboot.theater.repository.ScreeningRepository;
import com.ceos24.springboot.theater.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.user.repository.UserRepository;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private static final int HOLD_MINUTES = 10;

    private final ReservationRepository reservationRepository;
    private final ScreeningRepository screeningRepository;
    private final PriceRepository priceRepository;

    private final SeatRepository seatRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    private final UserRepository userRepository;
    private final ReservationExpirationService expirationService;

    // 예매 생성
    @Transactional
    public ReservationResponse createReservation(
            Long userId,
            ReservationCreateRequest request
    ){
        // 첫 DB 조회 전에 별도 트랜잭션에서 만료된 점유를 해제한다.
        expirationService.releaseExpiredSeats(request.screeningId(), request.seatIds());

        // 로그인 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다. userId=" + userId
                        )
                );

        // 1. 상영회차 조회
        Screening screening = screeningRepository
                .findById(request.screeningId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 상영회차를 찾을 수 없습니다. screeningId="
                                        + request.screeningId()
                        )
                );

        // 2. 좌석이 선택되었는지 확인
        if (request.seatIds() == null
                || request.seatIds().isEmpty()) {

            throw new IllegalArgumentException(
                    "좌석을 하나 이상 선택해야 합니다."
            );
        }

        // 3. 요청 자체에 중복된 seatId가 있는지 검사
        long distinctSeatCount =
                request.seatIds()
                        .stream()
                        .distinct()
                        .count();

        if (distinctSeatCount != request.seatIds().size()) {
            throw new IllegalArgumentException(
                    "동일한 좌석을 중복 선택할 수 없습니다."
            );
        }

        // 4. 총 예매 인원 수와 좌석 수가 같은지 확인
        int totalPeople =
                request.childCount()
                        + request.youthCount()
                        + request.adultCount()
                        + request.seniorCount();

        if (totalPeople != request.seatIds().size()) {
            throw new IllegalArgumentException(
                    "예매 인원 수와 선택한 좌석 수가 일치하지 않습니다."
            );
        }

        // 5. 실제 해당 상영관에 속하는 좌석 조회
        List<Seat> seats =
                seatRepository.findAllBySeatIdInAndScreenId_ScreenId(
                        request.seatIds(),
                        screening.getScreen().getScreenId()
                );

        // 6. 존재하지 않거나 다른 상영관 좌석인지 확인
        if (seats.size() != request.seatIds().size()) {
            throw new IllegalArgumentException(
                    "해당 상영관에 존재하지 않는 좌석이 포함되어 있습니다."
            );
        }

        // 7. 이미 예약된 좌석인지 확인
        List<ReservationSeat> reservedSeats =
                reservationSeatRepository
                        .findAllByScreening_ScreeningIdAndSeat_SeatIdIn(
                                screening.getScreeningId(),
                                request.seatIds()
                        );

        if (!reservedSeats.isEmpty()) {
            throw new GlobalException(ErrorCode.SEAT_UNAVAILABLE);
        }


        // 8. 가격 계산에 필요한 조건 확인
        DayType dayType =
                getDayType(screening.getScreeningDate());

        TimeType timeType =
                getTimeType(screening.getStartTime());

        ScreenType screenType =
                screening.getScreen().getScreenType();


        // 9. 총 가격 계산
        int totalPrice = 0;

        totalPrice += calculatePrice(
                AudienceType.Child,
                request.childCount(),
                dayType,
                timeType,
                screenType
        );

        totalPrice += calculatePrice(
                AudienceType.Youth,
                request.youthCount(),
                dayType,
                timeType,
                screenType
        );

        totalPrice += calculatePrice(
                AudienceType.Adult,
                request.adultCount(),
                dayType,
                timeType,
                screenType
        );

        totalPrice += calculatePrice(
                AudienceType.Senior,
                request.seniorCount(),
                dayType,
                timeType,
                screenType
        );

        // 10. Reservation 생성
        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = Reservation.builder()
                .user(user)
                .screening(screening)
                .childCount(request.childCount())
                .youthCount(request.youthCount())
                .adultCount(request.adultCount())
                .seniorCount(request.seniorCount())
                .totalPrice(totalPrice)
                .status(ReservationStatus.Pending)
                .reservationAt(now)
                .expiresAt(now.plusMinutes(HOLD_MINUTES))
                .build();

        // 11. Reservation 저장
        Reservation savedReservation =
                reservationRepository.save(reservation);

        // 12. ReservationSeat 생성
        List<ReservationSeat> reservationSeats =
                seats.stream()
                        .sorted(Comparator.comparing(Seat::getSeatId))
                        .map(seat ->
                                ReservationSeat.create(
                                        savedReservation,
                                        screening,
                                        seat
                                )
                        )
                        .toList();

        // 13. ReservationSeat 저장 및 UNIQUE 위반 여부 판단
        try {
            reservationSeatRepository.saveAllAndFlush(reservationSeats);
        } catch (DataIntegrityViolationException e) {
            // 동시에 들어온 요청은 UNIQUE(screening_id, seat_id)가 최종 차단한다.
            throw new GlobalException(ErrorCode.SEAT_UNAVAILABLE);
        }

        return ReservationResponse.from(savedReservation,seats);
    }


    // 관람객 유형별 가격 계산
    private int calculatePrice(
            AudienceType audienceType,
            Integer count,
            DayType dayType,
            TimeType timeType,
            ScreenType screenType
    ) {

        if (count == null || count == 0) {
            return 0;
        }

        Price price = priceRepository
                .findByAudienceTypeAndDayTypeAndTimeTypeAndScreenCategory(
                        audienceType,
                        dayType,
                        timeType,
                        screenType
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 조건의 가격 정보가 없습니다."
                        )
                );

        return price.getTicketPrice() * count;
    }


    // 평일 / 주말 판단
    private DayType getDayType(LocalDate date) {

        DayOfWeek dayOfWeek = date.getDayOfWeek();

        if (dayOfWeek == DayOfWeek.SATURDAY
                || dayOfWeek == DayOfWeek.SUNDAY) {
            return DayType.Weekend;
        }

        return DayType.Weekday;
    }


    // 조조 / 일반 시간 판단
    private TimeType getTimeType(LocalTime startTime) {

        // 예: 오전 10시 이전이면 조조
        if (startTime.isBefore(LocalTime.of(10, 0))) {
            return TimeType.Morning;
        }

        return TimeType.Normal;
    }

    // 예매 단건 조회(예매번호로 확인)
    public ReservationResponse getReservation(Long userId, Long reservationId) {
        expirationService.releaseExpiredReservationsForUser(userId);

        // 로그인한 사용자의 예매인지 확인 후 조회
        Reservation reservation = findMyReservation(userId, reservationId);

        // 예매에 연결된 좌석 조회 및 변환
        List<Seat> seats = reservationSeatRepository
                .findAllByReservation_ReservationId(reservation.getReservationId())
                .stream()
                .map(ReservationSeat::getSeat)
                .toList();

        return ReservationResponse.from(reservation, seats);
    }

    // 사용자 본인의 예매 조회
    private Reservation findMyReservation(
            Long userId,
            Long reservationId
    ) {
        return reservationRepository
                .findByReservationIdAndUser_Id(reservationId, userId)
                .orElseThrow(() ->
                        new GlobalException(ErrorCode.RESERVATION_NOT_FOUND)
                );
    }


    // 예매 취소
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

    // 예매 목록 조회
    public List<ReservationResponse> getMyReservations(Long userId) {
        expirationService.releaseExpiredReservationsForUser(userId);
        // 로그인 사용자의 예매만 조회
        List<Reservation> reservations = reservationRepository
                .findAllByUser_IdOrderByReservationAtDesc(userId);

        // 예매가 없으면 빈 목록 반환
        if (reservations.isEmpty()) {
            return List.of();
        }

        // 본인의 예매 ID 목록 추출
        List<Long> reservationIds = reservations.stream()
                .map(Reservation::getReservationId)
                .toList();

        // 좌석을 일괄 조회하고 예매 ID별로 묶기
        Map<Long, List<Seat>> seatsByReservation =
                reservationSeatRepository
                        .findAllByReservation_ReservationIdIn(reservationIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                reservationSeat ->
                                        reservationSeat.getReservation()
                                                .getReservationId(),
                                Collectors.mapping(
                                        ReservationSeat::getSeat,
                                        Collectors.toList()
                                )
                        ));

        // 예매별 좌석을 연결해 응답 DTO로 변환
        return reservations.stream()
                .map(reservation -> ReservationResponse.from(
                        reservation,
                        seatsByReservation.getOrDefault(
                                reservation.getReservationId(),
                                List.of()
                        )
                ))
                .toList();
    }


}
