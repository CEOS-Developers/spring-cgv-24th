package com.ceos24.springboot.reservation.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.reservation.domain.*;
import com.ceos24.springboot.reservation.dto.ReservationCreateRequest;
import com.ceos24.springboot.reservation.dto.ReservationResponse;
import com.ceos24.springboot.reservation.repository.PriceRepository;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.repository.ReservationSeatRepository;
import com.ceos24.springboot.theater.domain.ScreenType;
import com.ceos24.springboot.theater.domain.Screening;
import com.ceos24.springboot.theater.domain.ScreeningSeat;
import com.ceos24.springboot.theater.domain.Seat;
import com.ceos24.springboot.theater.repository.ScreeningRepository;
import com.ceos24.springboot.theater.repository.SeatRepository;
import com.ceos24.springboot.theater.repository.ScreeningSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.user.repository.UserRepository;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class ReservationCreateService {
    private static final int HOLD_MINUTES = 10;

    private final Clock clock;
    private final ReservationRepository reservationRepository;
    private final ScreeningRepository screeningRepository;
    private final PriceRepository priceRepository;
    private final SeatRepository seatRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final UserRepository userRepository;
    private final ScreeningSeatRepository screeningSeatRepository;

    @Transactional
    public ReservationResponse createReservation(
            Long userId,
            ReservationCreateRequest request
    ){
        List<ScreeningSeat> lockedSeats = screeningSeatRepository.findAllWithLock(
                request.screeningId(), request.seatIds());
        if (lockedSeats.size() != request.seatIds().size()) {
            throw new GlobalException(ErrorCode.SCREENING_SEAT_NOT_FOUND);
        }

        // 로그인 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다. userId=" + userId
                        )
                );

        //  상영회차 조회
        Screening screening = screeningRepository
                .findById(request.screeningId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 상영회차를 찾을 수 없습니다. screeningId="
                                        + request.screeningId()
                        )
                );

        //  실제 해당 상영관에 속하는 좌석 조회
        List<Seat> seats =
                seatRepository.findAllBySeatIdInAndScreenId_ScreenId(
                        request.seatIds(),
                        screening.getScreen().getScreenId()
                );

        // 존재하지 않거나 다른 상영관 좌석인지 확인
        if (seats.size() != request.seatIds().size()) {
            throw new IllegalArgumentException(
                    "해당 상영관에 존재하지 않는 좌석이 포함되어 있습니다."
            );
        }

        // 이미 예약된 좌석인지 확인
        List<ReservationSeat> reservedSeats =
                reservationSeatRepository
                        .findAllByScreening_ScreeningIdAndSeat_SeatIdIn(
                                screening.getScreeningId(),
                                request.seatIds()
                        );

        if (!reservedSeats.isEmpty()) {
            throw new GlobalException(ErrorCode.SEAT_UNAVAILABLE);
        }


        // 가격 계산에 필요한 조건 확인
        DayType dayType =
                getDayType(screening.getScreeningDate());

        TimeType timeType =
                getTimeType(screening.getStartTime());

        ScreenType screenType =
                screening.getScreen().getScreenType();


        // 총 가격 계산
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

        // Reservation 생성
        LocalDateTime now = LocalDateTime.now(clock);
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

        // Reservation 저장
        Reservation savedReservation =
                reservationRepository.save(reservation);

        // ReservationSeat 생성
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

        // ReservationSeat 저장 및 UNIQUE 위반 여부 판단
        try {
            reservationSeatRepository.saveAllAndFlush(reservationSeats);
        } catch (DataIntegrityViolationException e) {
            // 동시에 들어온 요청은 UNIQUE(screening_id, seat_id)가 최종 차단한다.
            throw new GlobalException(ErrorCode.SEAT_UNAVAILABLE);
        }

        return ReservationResponse.from(savedReservation,seats);
    }


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


    // 평일과 주말 판단
    private DayType getDayType(LocalDate date) {

        DayOfWeek dayOfWeek = date.getDayOfWeek();

        if (dayOfWeek == DayOfWeek.SATURDAY
                || dayOfWeek == DayOfWeek.SUNDAY) {
            return DayType.Weekend;
        }

        return DayType.Weekday;
    }


    // 조조와 일반 시간 판단
    private TimeType getTimeType(LocalTime startTime) {

        // 예: 오전 10시 이전이면 조조
        if (startTime.isBefore(LocalTime.of(10, 0))) {
            return TimeType.Morning;
        }

        return TimeType.Normal;
    }

}
