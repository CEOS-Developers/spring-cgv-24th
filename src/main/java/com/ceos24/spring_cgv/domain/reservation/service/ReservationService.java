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
     * 함수 기능: 예매에서 지정한 좌석들의 점유를 해제한다.
     *          남은 점유 좌석이 없으면 예매도 취소 상태로 전이한다.
     * @param reservation 대상 예매
     * @param seats 점유를 해제할 좌석 행
     */
    @Transactional
    public void unreserveSeats(Reservation reservation, List<ReservationSeat> seats) {

        seats.forEach(ReservationSeat::unreserve);

        // 같은 영속성 컨텍스트이므로 위에서 바꾼 상태가 그대로 보인다.
        boolean noneReserved = reservationSeatRepository.findAllByReservationId(reservation.getId())
                .stream()
                .noneMatch(ReservationSeat::isReserved);

        if (noneReserved) {
            reservation.cancel();
        }

        log.info("[좌석 점유 해제] reservationId={}, seatCount={}, 예매취소={}",
                reservation.getId(), seats.size(), noneReserved);
    }

    /***
     * 함수 기능: 예매의 모든 좌석 점유를 해제한다. 결제를 마치지 못한 예매를 되돌릴 때 쓴다.
     * @param reservation 점유를 해제할 예매
     */
    @Transactional
    public void release(Reservation reservation) {

        unreserveSeats(reservation, reservationSeatRepository.findAllByReservationId(reservation.getId()));
    }
}
