package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.member.domain.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.reservation.domain.Reservation;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.theater.domain.Seat;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ReservationService {

    private final SeatRepository seatRepository;
    private final MemberRepository memberRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public void reserveSeat(Long memberId, Long screeningId, Long seatNumber) {
        Seat seat = seatRepository.findByScreeningIdAndSeatNumber(screeningId, seatNumber);
        seat.reserveSeat();
        Member member = memberRepository.findById(memberId).orElseThrow();
        Reservation reservation = new Reservation(member, seat);
        reservationRepository.save(reservation);
    }

    @Transactional
    public void cancelReservation(Long memberId, Long screeningId, Long seatNumber) {
        Reservation reservation = reservationRepository.findReservationToCancel(memberId, screeningId, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예매이거나 권한이 없습니다."));

        Seat seat = reservation.getSeat();
        seat.cancelReservation();

        reservationRepository.delete(reservation);
    }
}
