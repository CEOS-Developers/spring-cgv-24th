package com.ceos24.springboot.reservation.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationSeat;
import com.ceos24.springboot.reservation.dto.ReservationResponse;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.repository.ReservationSeatRepository;
import com.ceos24.springboot.theater.domain.Seat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationQueryService {
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    public ReservationResponse getReservation(Long userId, Long reservationId) {

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


    // 예매 목록 조회
    public List<ReservationResponse> getMyReservations(Long userId) {
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
