package com.ceos24.cgv.domain.reservation.scheduler;

import com.ceos24.cgv.domain.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

    private final ReservationService reservationService;

    @Scheduled(fixedDelay = 10000)
    public void expireOverdueHolds() {
        reservationService.expireOverdueHolds();
    }
}
