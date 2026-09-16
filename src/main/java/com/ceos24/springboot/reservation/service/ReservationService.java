package com.ceos24.springboot.reservation.service;

import com.ceos24.springboot.reservation.domain.*;
import com.ceos24.springboot.reservation.dto.ReservationCreateRequest;
import com.ceos24.springboot.reservation.dto.ReservationResponse;
import com.ceos24.springboot.reservation.repository.PriceRepository;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.theater.domain.ScreenType;
import com.ceos24.springboot.theater.domain.Screening;
import com.ceos24.springboot.theater.repository.ScreeningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ScreeningRepository screeningRepository;
    private final PriceRepository priceRepository;

    // 예매 생성
    @Transactional
    public ReservationResponse createReservation(
            ReservationCreateRequest request
    ) {

        // 1. 상영회차 조회
        Screening screening = screeningRepository
                .findById(request.screeningId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 상영회차를 찾을 수 없습니다. screeningId="
                                        + request.screeningId()
                        )
                );

        // 2. 가격 계산에 필요한 조건 확인
        DayType dayType =
                getDayType(screening.getScreeningDate());

        TimeType timeType =
                getTimeType(screening.getStartTime());

        ScreenType screenType =
                screening.getScreen().getScreenType();

        // 3. 총 가격 계산
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

        // 4. Reservation 생성
        Reservation reservation = Reservation.builder()
                // User는 다음 주 로그인 구현 후 연결
                .screening(screening)
                .childCount(request.childCount())
                .youthCount(request.youthCount())
                .adultCount(request.adultCount())
                .seniorCount(request.seniorCount())
                .seatNumbers(request.seatNumbers())
                .totalPrice(totalPrice)
                .status(ReservationStatus.Reservated)
                .reservationAt(LocalDateTime.now())
                .build();

        // 5. 저장
        Reservation savedReservation =
                reservationRepository.save(reservation);

        return ReservationResponse.from(savedReservation);
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
}