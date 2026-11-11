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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
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
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public ReservationResDTO createReservation(Long memberId, ReservationReqDTO.CreateReservationDTO request) {
        List<Long> seatIds = request.screeningSeatIds();
        validateSeatIds(seatIds);

        Screening screening = screeningRepository.findById(request.screeningId())
                .orElseThrow(() -> new CustomException(ErrorCode.SCREENING_NOT_FOUND));
        screening.validateReservationTime(LocalDateTime.now(clock));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        List<ScreeningSeat> seats = lockAvailableSeats(screening.getId(), seatIds);

        // 좌석 잠금을 기다리는 동안 상영이 시작될 수 있으므로 저장 직전에 다시 확인한다.
        screening.validateReservationTime(LocalDateTime.now(clock));

        long totalPrice = seats.stream().mapToLong(ScreeningSeat::getPrice).sum();
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .member(member)
                .screening(screening)
                .totalPrice(totalPrice)
                .reservedAt(LocalDateTime.now(clock))
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
        reservation.validateCancellation(LocalDateTime.now(clock));

        // 현재 이 예매가 점유한 좌석만 해제하고 상태를 함께 변경한다.
        List<ScreeningSeat> seats = screeningSeatRepository.findAllByReservationIdForUpdate(reservationId);
        // 좌석 잠금 대기 후의 시각으로 취소 가능 여부를 다시 검증한다.
        reservation.cancel(LocalDateTime.now(clock));
        seats.forEach(ScreeningSeat::release);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void releaseUnpaidSeats() {
        LocalDateTime now = LocalDateTime.now(clock);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        List<Long> reservationIds = transaction.execute(status -> reservationRepository.findReservationIdsForSeatRelease(
                ReservationStatus.PENDING_PAYMENT, ReservationStatus.PAYMENT_FAILED, now));
        for (Long reservationId : reservationIds) {
            try {
                transaction.executeWithoutResult(status -> {
                    Reservation reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
                    if (reservation != null && reservation.releaseSeats(now)) {
                        screeningSeatRepository.findAllByReservationIdForUpdate(reservationId)
                                .forEach(ScreeningSeat::release);
                    }
                });
            } catch (RuntimeException error) {
                log.warn("미결제 예매 좌석 해제 실패: reservationId={}", reservationId, error);
            }
        }
    }

    @Transactional
    public void cancelPaidReservation(Reservation reservation, Map<Long, Integer> quantities, boolean fullyCancelled) {
        Map<Long, ScreeningSeat> occupiedSeats = new HashMap<>();
        for (ScreeningSeat seat : screeningSeatRepository.findAllByReservationIdForUpdate(reservation.getId())) {
            occupiedSeats.put(seat.getId(), seat);
        }
        for (ReservationSeat item : reservationSeatRepository
                .findAllByReservation_IdOrderByScreeningSeat_IdAsc(reservation.getId())) {
            Long seatId = item.getScreeningSeat().getId();
            if (item.isCancelled() || (!quantities.isEmpty() && !quantities.containsKey(seatId))) {
                continue;
            }
            item.cancel();
            ScreeningSeat occupiedSeat = occupiedSeats.get(seatId);
            if (occupiedSeat != null) {
                occupiedSeat.release();
            }
        }
        if (fullyCancelled) reservation.cancelPayment(LocalDateTime.now(clock));
        else reservation.partiallyCancelPayment();
    }

    public long calculateCancellationAmount(Reservation reservation, Map<Long, Integer> quantities) {
        List<ReservationSeat> seats = reservationSeatRepository
                .findAllByReservation_IdOrderByScreeningSeat_IdAsc(reservation.getId()).stream()
                .filter(seat -> quantities.containsKey(seat.getScreeningSeat().getId()) && !seat.isCancelled()).toList();
        if (seats.size() != quantities.size()) throw new CustomException(ErrorCode.PAYMENT_CANCEL_INVALID);
        return seats.stream().mapToLong(ReservationSeat::getPrice).sum();
    }

    private void validateSeatIds(List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty() || seatIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private List<ScreeningSeat> lockAvailableSeats(Long screeningId, List<Long> seatIds) {
        // 겹치는 좌석 요청도 같은 ID 순서로 잠가 교착 상태 가능성을 줄인다.
        List<ScreeningSeat> seats = new ArrayList<>();
        for (Long seatId : seatIds.stream().sorted().toList()) {
            ScreeningSeat seat = screeningSeatRepository.findByIdAndScreeningIdForUpdate(seatId, screeningId)
                    .orElseThrow(() -> new CustomException(ErrorCode.SCREENING_SEAT_NOT_FOUND));
            seat.validateAvailable();
            seats.add(seat);
        }
        return seats;
    }
}
