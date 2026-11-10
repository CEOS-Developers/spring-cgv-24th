package com.ceos24.springboot.payment.service.target;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.PaymentTargetType;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.service.ReservationLifecycleService;
import com.ceos24.springboot.shop.repository.StoreOrderRepository;
import com.ceos24.springboot.shop.service.StoreOrderLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentTargetResolver {
    private final ReservationRepository reservations;
    private final ReservationLifecycleService reservationLifecycle;
    private final StoreOrderRepository storeOrders;
    private final StoreOrderLifecycleService storeOrderLifecycle;

    // 호출자의 트랜잭션에서 구매 대상부터 잠근다. Payment 및 취소 이력은 이후에 잠근다.
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentTarget lock(PaymentTargetType type, Long targetId, Long userId) {
        return switch (type) {
            case RESERVATION -> new ReservationPaymentTarget(
                    reservations.findMyReservationWithLock(targetId, userId)
                            .orElseThrow(() -> new GlobalException(ErrorCode.RESERVATION_NOT_FOUND)),
                    reservationLifecycle);
            case STORE_ORDER -> new StoreOrderPaymentTarget(
                    storeOrders.findMyOrderWithLock(targetId, userId)
                            .orElseThrow(() -> new GlobalException(ErrorCode.STORE_ORDER_NOT_FOUND)),
                    storeOrderLifecycle);
        };
    }
}
