package com.ceos24.springboot.payment.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.*;
import com.ceos24.springboot.payment.dto.*;
import com.ceos24.springboot.payment.repository.PaymentRepository;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.repository.ReservationSeatRepository;
import com.ceos24.springboot.shop.domain.StoreOrder;
import com.ceos24.springboot.shop.domain.StoreOrderStatus;
import com.ceos24.springboot.shop.repository.StoreOrderRepository;
import com.ceos24.springboot.shop.service.StoreOrderLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentConfirmTransactionService {
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final StoreOrderLifecycleService storeOrderLifecycle;

    public record ConfirmContext(
            Long userId,
            PaymentTargetType targetType,
            Long targetId,
            String orderId,
            String paymentKey,
            Long amount,
            String idempotencyKey,
            boolean lookupOnly,
            PaymentConfirmResponse completed
    ) {}

    @Transactional
    public ConfirmContext begin(Long userId, PaymentConfirmRequest request) {
        // Read only
        var reference = paymentRepository.findTarget(request.orderId(), userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_NOT_FOUND));
        LockedTarget target = lockTarget(reference.getTargetType(), reference.getTargetId(), userId);
        Payment payment = findPayment(request.orderId(), userId);
        validateRequest(payment, target, request);

        if (payment.getStatus() == PaymentStatus.DONE) {
            if (!target.isCompleted()) {
                throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
            }
            return context(userId, target, payment, false, target.response(payment));
        }

        switch (payment.getStatus()) {
            case READY -> {
                LocalDateTime now = LocalDateTime.now();
                if (payment.getExpiresAt() == null || !payment.getExpiresAt().isAfter(now)) {
                    throw new GlobalException(target.expiredError());
                }
                target.begin(now);
                payment.startProcessing(request.paymentKey());
            }
            case UNKNOWN -> {
                validateHeldTarget(target);
                payment.startProcessing(request.paymentKey());
            }
            case PROCESSING -> {
                validateHeldTarget(target);
                return context(userId, target, payment, true, null);
            }
            default -> throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
        return context(userId, target, payment, false, null);
    }

    @Transactional
    public PaymentConfirmResponse finish(ConfirmContext context, TossPaymentConfirmResponse remote) {
        // Every path locks the purchase first, then Payment, then stock if needed.
        LockedTarget target = lockTarget(context.targetType(), context.targetId(), context.userId());
        Payment payment = findPayment(context.orderId(), context.userId());
        if (payment.getStatus() == PaymentStatus.DONE || payment.getStatus() == PaymentStatus.FAILED) {
            return target.response(payment);
        }
        validateHeldTarget(target);

        if (!matches(payment, remote)) {
            payment.markUnknown();
        } else if ("DONE".equals(remote.status()) && remote.approvedAt() != null) {
            LocalDateTime approvedAt = remote.approvedAt()
                    .atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
            payment.complete(approvedAt, remote.method());
            target.complete();
        } else if ("ABORTED".equals(remote.status()) || "EXPIRED".equals(remote.status())) {
            payment.markFailed();
            if (target.reservation() != null) {
                Reservation reservation = target.reservation();
                reservation.endPaymentProcessing();
                if (reservation.expireIfPending(LocalDateTime.now())) {
                    reservationSeatRepository.deleteAllByReservation_ReservationId(reservation.getReservationId());
                }
            } else {
                storeOrderLifecycle.failPaymentLocked(target.storeOrder(), LocalDateTime.now());
            }
        } else {
            // Never release stock/seats based only on a timeout or an unsupported state.
            payment.markUnknown();
        }
        return target.response(payment);
    }

    private LockedTarget lockTarget(PaymentTargetType type, Long id, Long userId) {
        return switch (type) {
            case RESERVATION -> new LockedTarget(reservationRepository.findMyReservationWithLock(id, userId)
                    .orElseThrow(() -> new GlobalException(ErrorCode.RESERVATION_NOT_FOUND)), null);
            case STORE_ORDER -> new LockedTarget(null, storeOrderRepository.findMyOrderWithLock(id, userId)
                    .orElseThrow(() -> new GlobalException(ErrorCode.STORE_ORDER_NOT_FOUND)));
        };
    }

    private Payment findPayment(String orderId, Long userId) {
        return paymentRepository.findMyPaymentWithLock(orderId, userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void validateRequest(Payment payment, LockedTarget target, PaymentConfirmRequest request) {
        if (payment.getTargetType() != target.type()
                || !Objects.equals(payment.getTargetId(), target.id())
                || !Objects.equals(payment.getAmount(), request.amount())
                || !Objects.equals(payment.getAmount(), target.amount())
                || (payment.getPaymentKey() != null && !payment.getPaymentKey().equals(request.paymentKey()))) {
            throw new GlobalException(ErrorCode.PAYMENT_INFORMATION_MISMATCH);
        }
    }

    private void validateHeldTarget(LockedTarget target) {
        if (!target.isHeld()) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    private boolean matches(Payment payment, TossPaymentConfirmResponse remote) {
        return remote != null
                && Objects.equals(payment.getPaymentKey(), remote.paymentKey())
                && Objects.equals(payment.getOrderId(), remote.orderId())
                && Objects.equals(payment.getAmount(), remote.totalAmount())
                && Objects.equals(payment.getCurrency(), remote.currency());
    }

    private ConfirmContext context(Long userId, LockedTarget target, Payment payment,
                                   boolean lookupOnly, PaymentConfirmResponse completed) {
        return new ConfirmContext(userId, target.type(), target.id(), payment.getOrderId(),
                payment.getPaymentKey(), payment.getAmount(), payment.getConfirmIdempotencyKey(),
                lookupOnly, completed);
    }

    // Domain-specific state transitions stay in the respective entities/services.
    private record LockedTarget(Reservation reservation, StoreOrder storeOrder) {
        PaymentTargetType type() {
            return reservation != null ? PaymentTargetType.RESERVATION : PaymentTargetType.STORE_ORDER;
        }

        Long id() {
            return reservation != null ? reservation.getReservationId() : storeOrder.getOrderId();
        }

        Long amount() {
            Integer value = reservation != null ? reservation.getTotalPrice() : storeOrder.getTotalAmount();
            return value == null ? null : value.longValue();
        }

        boolean isCompleted() {
            return reservation != null ? reservation.getStatus() == ReservationStatus.Reserved
                    : storeOrder.getStatus() == StoreOrderStatus.PAID;
        }

        boolean isHeld() {
            return reservation != null
                    ? reservation.getStatus() == ReservationStatus.Pending && reservation.isPaymentProcessing()
                    : storeOrder.getStatus() == StoreOrderStatus.PENDING && storeOrder.isPaymentProcessing();
        }

        ErrorCode expiredError() {
            return reservation != null ? ErrorCode.RESERVATION_EXPIRED : ErrorCode.STORE_ORDER_EXPIRED;
        }

        void begin(LocalDateTime now) {
            if (reservation != null) {
                reservation.beginPayment(now);
            } else {
                storeOrder.beginPayment(now);
            }
        }

        void complete() {
            if (reservation != null) {
                reservation.completePayment();
            } else {
                storeOrder.completePayment();
            }
        }

        PaymentConfirmResponse response(Payment payment) {
            return reservation != null ? PaymentConfirmResponse.from(payment, reservation)
                    : PaymentConfirmResponse.from(payment, storeOrder);
        }
    }
}
