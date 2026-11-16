package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReservationExpirationProcessor {

    private final ReservationRepository reservationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireSingleReservation(Long reservationId, LocalDateTime now) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || !reservation.isExpired(now)) {
            return false;
        }
        reservation.expire();
        reservationRepository.flush();
        return true;
    }
}
