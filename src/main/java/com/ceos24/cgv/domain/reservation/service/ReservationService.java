package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.reservation.dto.response.ReservationResponse;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.entity.SeatReservation;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.repository.SeatReservationRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ReservationService {

    private final MemberRepository memberRepository;
    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public ReservationResponse reserveSeats(Long memberId, Long screeningId, List<Long> seatNumbers) {
        LocalDateTime now = LocalDateTime.now();
        Member member = findMember(memberId);
        Screening screening = findScreening(screeningId);

        // 데드락 방지를 위한 중복 제거 및 좌석 번호 오름차순 정렬
        List<Long> sortedSeatNumbers = seatNumbers.stream().distinct().sorted().toList();

        List<Seat> seats = findAndValidateSeats(screening.getScreen().getId(), sortedSeatNumbers);
        validateAndReleaseExpiredSeats(screeningId, seats, now);

        Long ticketPrice = screening.getScreen().getScreenType().getTicketPrice();
        List<SeatReservation> seatReservations = seats.stream()
                .map(seat -> SeatReservation.create(screening, seat))
                .toList();

        Reservation reservation = Reservation.create(member, screening, seatReservations, ticketPrice, now);

        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_RESERVED);
        }

        return ReservationResponse.from(reservation);
    }

    @Transactional
    public void cancelReservation(Long memberId, Long reservationId) {
        Reservation reservation = findReservationWithSeats(reservationId, memberId);
        reservation.cancelWithoutPayment();
    }

    @Transactional
    public ReservationResponse cancelReservationSeats(Long memberId, Long reservationId, List<Long> seatNumbers) {
        Reservation reservation = findReservationWithSeats(reservationId, memberId);
        reservation.cancelSeatsWithoutPayment(seatNumbers, LocalDateTime.now());
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public int expireHoldReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> expiredReservations =
                reservationRepository.findAllExpiredHoldReservations(ReservationStatus.HOLD, now);

        if (expiredReservations.isEmpty()) {
            return 0;
        }

        expiredReservations.forEach(Reservation::expire);
        log.info("만료된 임시 좌석 점유 {}건 정리 완료", expiredReservations.size());
        return expiredReservations.size();
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Screening findScreening(Long screeningId) {
        return screeningRepository
                .findByIdWithScreen(screeningId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));
    }

    private List<Seat> findAndValidateSeats(Long screenId, List<Long> sortedSeatNumbers) {
        List<Seat> seats =
                seatRepository.findAllByScreenIdAndSeatNumberInOrderBySeatNumberAsc(screenId, sortedSeatNumbers);
        if (seats.size() != sortedSeatNumbers.size()) {
            throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
        }
        return seats;
    }

    private void validateAndReleaseExpiredSeats(Long screeningId, List<Seat> seats, LocalDateTime now) {
        List<Long> seatIds = seats.stream().map(Seat::getId).toList();
        List<SeatReservation> existingReservations =
                seatReservationRepository.findAllWithReservationByScreeningIdAndSeatIdIn(screeningId, seatIds);

        if (existingReservations.isEmpty()) {
            return;
        }

        boolean hasActiveHoldOrConfirmed = existingReservations.stream().anyMatch(sr -> sr.isActive(now));
        if (hasActiveHoldOrConfirmed) {
            throw new BusinessException(ErrorCode.ALREADY_RESERVED);
        }

        // 모두 만료된 HOLD 상태인 경우 즉시 만료 처리 및 SeatReservation 물리 삭제 반영
        List<Long> expiredReservationIds = existingReservations.stream()
                .map(sr -> sr.getReservation().getId())
                .distinct()
                .toList();

        List<Reservation> expiredReservations = reservationRepository.findAllWithSeatsByIdIn(expiredReservationIds);
        expiredReservations.forEach(Reservation::expire);
        try {
            reservationRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.ALREADY_RESERVED);
        }
    }

    private Reservation findReservationWithSeats(Long reservationId, Long memberId) {
        return reservationRepository
                .findByIdAndMemberIdWithSeats(reservationId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }
}
