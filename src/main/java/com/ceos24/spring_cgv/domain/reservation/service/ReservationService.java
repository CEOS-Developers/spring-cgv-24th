package com.ceos24.spring_cgv.domain.reservation.service;

import com.ceos24.spring_cgv.domain.member.entity.Member;
import com.ceos24.spring_cgv.domain.movie.entity.Screening;
import com.ceos24.spring_cgv.domain.reservation.dto.ReservationSeatCommand;
import com.ceos24.spring_cgv.domain.reservation.entity.Reservation;
import com.ceos24.spring_cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.spring_cgv.domain.reservation.exception.ReservationException;
import com.ceos24.spring_cgv.domain.reservation.exception.code.ReservationErrorCode;
import com.ceos24.spring_cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.spring_cgv.domain.reservation.repository.ReservationSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    /***
     * 함수 기능: 좌석을 점유(RESERVED)하여 예매를 생성한다. 결제창으로 넘어가기 전에 호출된다.
     * @param member 예매한 회원
     * @param screening 예매 대상 상영회차
     * @param totalPrice 예매 총 금액
     * @param seats 점유할 좌석 목록
     * @return 생성된 예매
     */
    @Transactional
    public Reservation reserve(Member member, Screening screening,
                               Integer totalPrice, List<ReservationSeatCommand> seats) {

        // 좌석 없는 예매가 만들어지면 금액만 있고 좌석이 없는 손상된 데이터가 된다.
        if (seats.isEmpty()) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_SEAT_EMPTY);
        }

        // 예매를 DB에 저장한다.
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .member(member)
                .screening(screening)
                .price(totalPrice)
                .build());

        // 각 좌석을 RESERVED 상태로 저장한다.
        reservationSeatRepository.saveAll(seats.stream()
                .map(seat -> ReservationSeat.builder()
                        .reservation(reservation)
                        .screening(screening)
                        .seatRow(seat.seatRow())
                        .seatNumber(seat.seatNumber())
                        .ageGroup(seat.ageGroup())
                        .price(seat.price())
                        .build())
                .toList());

        log.info("[좌석 점유] reservationId={}, screeningId={}, seatCount={}",
                reservation.getId(), screening.getId(), seats.size());

        return reservation;
    }

    /***
     * 함수 기능: 결제를 마치지 못한 예매의 좌석 점유를 해제하고 예매를 취소한다.
     *          이미 다른 예매가 가져간 좌석 행은 조회되지 않으므로 건드리지 않는다.
     * @param reservation 점유를 해제할 예매
     */
    @Transactional
    public void release(Reservation reservation) {

        List<ReservationSeat> seatRows =
                reservationSeatRepository.findAllByReservationId(reservation.getId());

        seatRows.forEach(ReservationSeat::unreserve);
        reservation.cancel();

        log.info("[좌석 점유 해제] reservationId={}, seatCount={}",
                reservation.getId(), seatRows.size());
    }
}
