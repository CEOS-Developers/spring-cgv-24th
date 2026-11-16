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

    /** 사용자 1명이 동시에 보유할 수 있는 결제 대기(HOLD) 예매 건수 상한 */
    private static final long MAX_ACTIVE_HOLDS = 3L;

    private final MemberRepository memberRepository;
    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationExpirationProcessor reservationExpirationProcessor;

    @Transactional
    public ReservationResponse reserveSeats(Long memberId, Long screeningId, List<Long> seatNumbers) {
        LocalDateTime now = LocalDateTime.now();
        Member member = findMember(memberId);
        Screening screening = findScreening(screeningId);

        if (!screening.getStartTime().isAfter(now)) {
            throw new BusinessException(ErrorCode.SCREENING_ALREADY_STARTED);
        }
        if (reservationRepository.countActiveHolds(memberId, ReservationStatus.HOLD, now) >= MAX_ACTIVE_HOLDS) {
            throw new BusinessException(ErrorCode.RESERVATION_HOLD_LIMIT_EXCEEDED);
        }

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
        Reservation reservation = findReservationWithLock(reservationId, memberId);
        reservation.cancelWithoutPayment();
    }

    @Transactional
    public ReservationResponse cancelReservationSeats(Long memberId, Long reservationId, List<Long> seatNumbers) {
        Reservation reservation = findReservationWithLock(reservationId, memberId);
        reservation.cancelSeatsWithoutPayment(seatNumbers, LocalDateTime.now());
        return ReservationResponse.from(reservation);
    }

    public int expireHoldReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> expiredReservationIds =
                reservationRepository.findAllExpiredHoldReservationIds(ReservationStatus.HOLD, now);

        if (expiredReservationIds.isEmpty()) {
            return 0;
        }

        int expiredCount = 0;
        for (Long reservationId : expiredReservationIds) {
            try {
                if (reservationExpirationProcessor.expireSingleReservation(reservationId, now)) {
                    expiredCount++;
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("예매 만료 정리 중 동시 상태 변경으로 스킵: reservationId={}", reservationId);
            } catch (Exception e) {
                log.error("단일 예매 만료 처리 중 오류 발생: reservationId={}, error={}", reservationId, e.getMessage(), e);
            }
        }

        if (expiredCount > 0) {
            log.info("만료된 임시 좌석 점유 {}건 정리 완료", expiredCount);
        }
        return expiredCount;
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
                .sorted()
                .toList();

        List<Reservation> expiredReservations = reservationRepository.findAllWithSeatsByIdIn(expiredReservationIds);
        expiredReservations.forEach(Reservation::expire);
        try {
            reservationRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.ALREADY_RESERVED);
        }
    }

    private Reservation findReservationWithLock(Long reservationId, Long memberId) {
        Reservation reservation = reservationRepository
                .findByIdWithLock(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        return reservation;
    }
}
