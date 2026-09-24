package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.reservation.config.SeatHoldProperties;
import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.domain.reservation.dto.SeatHoldResponse;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatHoldService {
    private final SeatHoldCreationService creationService;
    private final SeatHoldExpiryService expiryService;
    private final SeatHoldProperties properties;

    public HoldCreationResult create(Long userId, UUID requestKey, SeatHoldCreateRequest request) {
        for (int attempt = 0; attempt <= properties.maxSeats(); attempt++) {
            try {
                return creationService.create(userId, requestKey, request);
            } catch (SeatHoldCreationService.ExpiredHoldEncountered expired) {
                boolean cleaned = expiryService.expireIfElapsed(expired.reservationId());
                if (expired.sameRequestKey() && cleaned) {
                    throw new BusinessException(ErrorCode.HOLD_EXPIRED);
                }
            }
        }
        throw new BusinessException(ErrorCode.SEAT_BUSY);
    }

    public record HoldCreationResult(SeatHoldResponse response, boolean created) {
    }
}
