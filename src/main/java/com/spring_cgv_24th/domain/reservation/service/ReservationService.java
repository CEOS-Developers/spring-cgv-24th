package com.spring_cgv_24th.domain.reservation.service;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.domain.reservation.dto.ReservationReqDTO;
import com.spring_cgv_24th.domain.reservation.dto.ReservationResDTO;
import com.spring_cgv_24th.domain.reservation.entity.Reservation;
import com.spring_cgv_24th.domain.reservation.entity.ReservationSeat;
import com.spring_cgv_24th.domain.reservation.enums.ReservationStatus;
import com.spring_cgv_24th.domain.reservation.repository.ReservationRepository;
import com.spring_cgv_24th.domain.reservation.repository.ReservationSeatRepository;
import com.spring_cgv_24th.domain.screening.entity.Screening;
import com.spring_cgv_24th.domain.screening.entity.ScreeningSeat;
import com.spring_cgv_24th.domain.screening.repository.ScreeningRepository;
import com.spring_cgv_24th.domain.screening.repository.ScreeningSeatRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final MemberRepository memberRepository;
    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatRepository screeningSeatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final Clock clock;

    @Transactional
    public ReservationResDTO createReservation(ReservationReqDTO.CreateReservationDTO request) {
        List<Long> seatIds = request.screeningSeatIds();
        if (seatIds == null || seatIds.isEmpty() || seatIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        Screening screening = screeningRepository.findById(request.screeningId())
                .orElseThrow(() -> new CustomException(ErrorCode.SCREENING_NOT_FOUND));
        requireBeforeStart(screening, ErrorCode.SCREENING_ALREADY_STARTED);
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 서로 겹치는 좌석 요청도 항상 같은 ID 순서로 잠근다. 동시성 문제 방지
        List<ScreeningSeat> seats = new ArrayList<>();
        for (Long seatId : seatIds.stream().sorted().toList()) {
            ScreeningSeat seat = screeningSeatRepository
                    .findByIdAndScreeningIdForUpdate(seatId, screening.getId())
                    .orElseThrow(() -> new CustomException(ErrorCode.SCREENING_SEAT_NOT_FOUND));
            if (seat.getReservation() != null) {
                throw new CustomException(ErrorCode.SEAT_ALREADY_RESERVED);
            }
            seats.add(seat);
        }

        // 좌석 잠금을 기다리는 동안 상영이 시작될 수 있으므로 저장 직전에 다시 확인한다.
        requireBeforeStart(screening, ErrorCode.SCREENING_ALREADY_STARTED);

        long totalPrice = seats.stream().mapToLong(ScreeningSeat::getPrice).sum();
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .member(member)
                .screening(screening)
                .totalPrice(totalPrice)
                .build());
        seats.forEach(seat -> seat.occupy(reservation));
        reservationSeatRepository.saveAll(seats.stream()
                .map(seat -> ReservationSeat.builder()
                        .reservation(reservation)
                        .screeningSeat(seat)
                        .price(seat.getPrice())
                        .build())
                .toList());

        return ReservationResDTO.from(reservation, seats);
    }

    @Transactional
    public void cancelReservation(Long reservationId, Long memberId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
        Member owner = reservation.getMember();
        if (owner == null || !Objects.equals(owner.getId(), memberId)) {
            throw new CustomException(ErrorCode.RESERVATION_FORBIDDEN);
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new CustomException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
        }
        requireBeforeStart(reservation.getScreening(), ErrorCode.RESERVATION_CANCELLATION_CLOSED);

        // 현재 이 예매가 점유한 좌석만 해제하고 상태를 함께 변경한다.
        List<ScreeningSeat> seats = screeningSeatRepository.findAllByReservationIdForUpdate(reservationId);
        LocalDateTime cancelledAt = requireBeforeStart(
                reservation.getScreening(), ErrorCode.RESERVATION_CANCELLATION_CLOSED);
        seats.forEach(ScreeningSeat::release);
        reservation.cancel(cancelledAt);
    }

    private LocalDateTime requireBeforeStart(Screening screening, ErrorCode errorCode) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(screening.getStartsAt())) {
            throw new CustomException(errorCode);
        }
        return now;
    }
}
