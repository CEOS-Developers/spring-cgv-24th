package com.ceos.cgv.domain.reservation.service.hold;

import com.ceos.cgv.domain.reservation.config.SeatHoldProperties;
import com.ceos.cgv.domain.reservation.dto.ExpiredHoldCandidate;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatHoldCleanupService {
    private final ReservationRepository reservationRepository;
    private final SeatHoldExpiryService expiryService;
    private final SeatHoldProperties properties;
    private final Clock seatHoldClock;
    private ExpiredHoldCandidate lastScanned;

    public synchronized void cleanup() {
        Instant now = seatHoldClock.instant();
        var page = PageRequest.of(0, properties.cleanupBatchSize());
        List<ExpiredHoldCandidate> candidates = findCandidates(now, page);
        if (candidates.isEmpty() && lastScanned != null) {
            lastScanned = null;
            candidates = findCandidates(now, page);
        }
        if (candidates.isEmpty()) {
            return;
        }
        lastScanned = candidates.getLast();
        for (ExpiredHoldCandidate candidate : candidates) {
            Long reservationId = candidate.reservationId();
            try {
                expiryService.expireIfElapsed(reservationId);
            } catch (BusinessException exception) {
                if (exception.getErrorCode() == ErrorCode.SEAT_BUSY) {
                    log.debug("Seat hold {} is locked; retrying on next cleanup", reservationId);
                } else {
                    log.warn("Could not expire seat hold {}: {}",
                            reservationId, exception.getErrorCode());
                }
            }
        }
    }

    private List<ExpiredHoldCandidate> findCandidates(Instant now, PageRequest page) {
        return reservationRepository.findExpiredHoldCandidates(now,
                lastScanned == null ? null : lastScanned.expiresAt(),
                lastScanned == null ? null : lastScanned.reservationId(), page);
    }
}
