package com.ceos.cgv.domain.reservation.scheduler;

import com.ceos.cgv.domain.reservation.service.hold.SeatHoldCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeatHoldCleanupScheduler {
    private final SeatHoldCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${cgv.seat-hold.cleanup-delay-ms:60000}",
            initialDelayString = "${cgv.seat-hold.cleanup-delay-ms:60000}")
    public void cleanup() {
        cleanupService.cleanup();
    }
}
