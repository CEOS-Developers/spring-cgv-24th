package com.ceos24.cgv.domain.reservation.scheduler;

import com.ceos24.cgv.domain.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

    private final ReservationService reservationService;

    @Scheduled(fixedDelay = 60000)
    public void releaseExpiredSeatHolds() {
        try {
            reservationService.expireHoldReservations();
        } catch (Exception e) {
            log.error("reservation_expire_scheduler_failed", e);
        }
    }
}
