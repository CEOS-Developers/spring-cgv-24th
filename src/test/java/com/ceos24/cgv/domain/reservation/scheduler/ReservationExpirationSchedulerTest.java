package com.ceos24.cgv.domain.reservation.scheduler;

import com.ceos24.cgv.domain.reservation.service.ReservationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationExpirationSchedulerTest {

    @Test
    void 만료_처리한_좌석_선점_건수를_누적_집계한다() {
        ReservationService reservationService = mock(ReservationService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReservationExpirationScheduler scheduler = new ReservationExpirationScheduler(reservationService, meterRegistry);
        when(reservationService.expireOverdueHolds()).thenReturn(3, 0, 2);

        scheduler.expireOverdueHolds();
        scheduler.expireOverdueHolds();
        scheduler.expireOverdueHolds();

        assertThat(meterRegistry.get("reservation.holds.expired").counter().count()).isEqualTo(5);
    }
}
