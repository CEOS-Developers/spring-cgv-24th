package com.ceos24.cgv.domain.reservation.scheduler;

import com.ceos24.cgv.domain.reservation.service.ReservationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpirationScheduler {

    private final ReservationService reservationService;
    private final Counter expiredHolds;

    public ReservationExpirationScheduler(ReservationService reservationService, MeterRegistry meterRegistry) {
        this.reservationService = reservationService;
        this.expiredHolds = Counter.builder("reservation.holds.expired")
                .description("결제 없이 만료된 좌석 선점 건수")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 10000)
    public void expireOverdueHolds() {
        expiredHolds.increment(reservationService.expireOverdueHolds());
    }
}
