package com.ceos24.springboot.payment.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.domain.PaymentTargetType;
import com.ceos24.springboot.payment.dto.PaymentPrepareResponse;
import com.ceos24.springboot.payment.dto.ReservationPaymentPrepareRequest;
import com.ceos24.springboot.payment.dto.StoreOrderPaymentPrepareRequest;
import com.ceos24.springboot.shop.domain.StoreOrder;
import com.ceos24.springboot.shop.repository.StoreOrderRepository;
import com.ceos24.springboot.shop.service.StoreOrderExpirationService;
import com.ceos24.springboot.payment.repository.PaymentRepository;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final StoreOrderExpirationService storeOrderExpirationService;

    @Transactional
    public PaymentPrepareResponse prepareStoreOrder(Long userId, StoreOrderPaymentPrepareRequest request) {
        if (request.storeOrderId() == null || request.storeOrderId() <= 0) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_TARGET_ID);
        }
        storeOrderExpirationService.releaseExpiredForUser(userId);
        StoreOrder order = storeOrderRepository.findMyOrderWithLock(request.storeOrderId(), userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.STORE_ORDER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        order.validatePayable(now);
        if (order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_AMOUNT);
        }
        Long amount = order.getTotalAmount().longValue();
        var existing = paymentRepository.findFirstByTargetTypeAndTargetIdOrderByIdDesc(
                PaymentTargetType.STORE_ORDER, order.getOrderId());
        if (existing.isPresent()) {
            validateReusablePayment(existing.get(), userId, amount, now);
            return PaymentPrepareResponse.from(existing.get());
        }
        return PaymentPrepareResponse.from(paymentRepository.save(Payment.prepare(order.getUser(),
                PaymentTargetType.STORE_ORDER, order.getOrderId(), amount, now, order.getExpiresAt())));
    }

    @Transactional
    public PaymentPrepareResponse prepareReservation(Long userId,ReservationPaymentPrepareRequest request) {
        Long reservationId = request.reservationId();

        if (reservationId == null || reservationId <= 0) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_TARGET_ID);
        }

        // 본인 예매만 조회하고, 같은 예매의 준비 요청을 순서대로 처리한다.
        Reservation reservation = reservationRepository
                .findMyReservationWithLock(reservationId, userId)
                .orElseThrow(() ->
                        new GlobalException(ErrorCode.RESERVATION_NOT_FOUND)
                );

        // 잠금을 기다리는 동안 시간이 지날 수 있으므로 조회 후 측정한다.
        LocalDateTime now = LocalDateTime.now();

        validateReservation(reservation, now);

        Long amount = reservation.getTotalPrice().longValue();

        // db에 저장된 티켓 금액으로 계산
        Optional<Payment> existingPayment = paymentRepository
                .findFirstByTargetTypeAndTargetIdOrderByIdDesc(
                        PaymentTargetType.RESERVATION,
                        reservationId
                );

        // 결제 금액이 있다면 재사용.
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();

            validateReusablePayment(payment, userId, amount, now);

            return PaymentPrepareResponse.from(payment);
        }

        Payment payment = Payment.prepare(
                reservation.getUser(),
                PaymentTargetType.RESERVATION,
                reservationId,
                amount,
                now,
                reservation.getExpiresAt()
        );

        Payment savedPayment = paymentRepository.save(payment);

        return PaymentPrepareResponse.from(savedPayment);
    }

    private void validateReservation(Reservation reservation, LocalDateTime now) {
        if (reservation.getStatus() == ReservationStatus.Expired) {
            throw new GlobalException(ErrorCode.RESERVATION_EXPIRED);
        }

        if (reservation.getStatus() != ReservationStatus.Pending) {
            throw new GlobalException(ErrorCode.RESERVATION_NOT_PAYABLE);
        }

        if (reservation.getExpiresAt() == null) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_EXPIRATION);
        }

        if (!reservation.getExpiresAt().isAfter(now)) {
            throw new GlobalException(ErrorCode.RESERVATION_EXPIRED);
        }

        if (reservation.getTotalPrice() == null
                || reservation.getTotalPrice() <= 0) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_AMOUNT);
        }
    }

    private void validateReusablePayment(
            Payment payment,
            Long userId,
            Long amount,
            LocalDateTime now
    ) {
        if (payment.getStatus() != PaymentStatus.READY
                || !payment.getUser().getId().equals(userId)
                || !payment.getAmount().equals(amount)
                || !payment.getExpiresAt().isAfter(now)) {
            throw new GlobalException(ErrorCode.PAYMENT_NOT_PREPARABLE);
        }
    }
}
