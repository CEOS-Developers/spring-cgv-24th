package com.spring_cgv_24th.domain.payment.service;

import com.spring_cgv_24th.domain.reservation.service.ReservationService;
import com.spring_cgv_24th.domain.store.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.expiration-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReleaseScheduler {
    private final PaymentService paymentService;
    private final StoreService storeService;
    private final ReservationService reservationService;

    @Scheduled(fixedDelay = 30_000)
    public void recoverPayments() {
        paymentService.recoverUnresolvedPayments();
    }

    @Scheduled(fixedDelay = 30_000)
    public void releaseStoreStock() {
        storeService.releaseUnpaidOrderStock();
    }

    @Scheduled(fixedDelay = 30_000)
    public void releaseReservationSeats() {
        reservationService.releaseUnpaidSeats();
    }
}
