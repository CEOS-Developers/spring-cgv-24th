package com.ceos24.springboot.reservation.service;

import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {
    private final Clock clock;
    private final ReservationRepository reservationRepository;
    private final ReservationExpirationService expirationService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expirePendingReservations() {
        // 한 번에 처리할 양을 제한하고 예매마다 별도 트랜잭션을 사용한다.
        for (Long reservationId : reservationRepository.findExpiredReservationIds(
                ReservationStatus.Pending, LocalDateTime.now(clock), PageRequest.of(0, 100))) {
            try {
                expirationService.expireReservation(reservationId);
            } catch (RuntimeException e) {
                log.error("대기 예매 만료 처리 실패: reservationId={}", reservationId, e);
            }
        }
    }
}
