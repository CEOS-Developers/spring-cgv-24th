package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReservationExpirationProcessor {

    private final ReservationRepository reservationRepository;

    /**
     * 호출하는 쪽(ReservationService.expireHoldReservations)에 바깥 트랜잭션이 없으므로 건마다 별도 트랜잭션이 된다.
     * 바깥 트랜잭션에서 호출하면 한 건의 실패가 전체 롤백으로 번지므로 호출하는 쪽에 @Transactional을 붙이지 않는다.
     */
    @Transactional
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
