package com.ceos24.springboot.payment.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.*;
import com.ceos24.springboot.payment.dto.*;
import com.ceos24.springboot.payment.config.TossPaymentProperties;
import com.ceos24.springboot.payment.repository.PaymentRepository;
import com.ceos24.springboot.payment.service.target.PaymentTarget;
import com.ceos24.springboot.payment.service.target.PaymentTargetResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentConfirmTransactionService {
    private final Clock clock;
    private final TossPaymentProperties properties;
    private final PaymentRepository paymentRepository;
    private final PaymentTargetResolver targetResolver;

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

        var reference = paymentRepository.findTarget(request.orderId(), userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_NOT_FOUND));
        PaymentTarget target = targetResolver.lock(reference.getTargetType(), reference.getTargetId(), userId);
        Payment payment = findPayment(request.orderId(), userId);
        validateRequest(payment, target, request);

        // FAILED를 INVALID_PAYMENT_STATUS로 거절
        if (payment.getStatus() == PaymentStatus.DONE) {
            if (!target.isCompleted()) {
                throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
            }
            return context(userId, target, payment, false, target.response(payment));
        }

        switch (payment.getStatus()) {
            case READY -> {
                LocalDateTime now = LocalDateTime.now(clock);
                if (payment.getExpiresAt() == null || !payment.getExpiresAt().isAfter(now)) {
                    throw new GlobalException(target.expiredError());
                }
                target.beginPayment(now);
                payment.startProcessing(request.paymentKey());
                payment.getRecovery().schedule(now.plus(properties.getRecovery().getProcessingTimeout()));
            }
            case UNKNOWN, PROCESSING -> {
                validateHeldTarget(target);
                return context(userId, target, payment, true, null);
            }
            default -> throw new GlobalException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
        return context(userId, target, payment, false, null);
    }

    @Transactional
    public Optional<ConfirmContext> claimRecovery(Long paymentId) {
        var identity = paymentRepository.findRecoveryReference(paymentId).orElse(null);
        if (identity == null) { return Optional.empty(); }
        var reference = paymentRepository.findTarget(identity.getOrderId(), identity.getUserId()).orElseThrow();
        PaymentTarget target = targetResolver.lock(reference.getTargetType(), reference.getTargetId(), identity.getUserId());
        Payment payment = findPayment(identity.getOrderId(), identity.getUserId());
        LocalDateTime now = LocalDateTime.now(clock);
        if ((payment.getStatus() != PaymentStatus.UNKNOWN && payment.getStatus() != PaymentStatus.PROCESSING)
                || !payment.getRecovery().isDue(now, payment.getCreatedAt(),
                now.minus(properties.getRecovery().getProcessingTimeout()))) {
            return Optional.empty();
        }
        validateHeldTarget(target);
        payment.getRecovery().claim(now.plus(properties.getRecovery().getLeaseDuration()));
        return Optional.of(context(identity.getUserId(), target, payment, true, null));
    }

    @Transactional
    public PaymentConfirmResponse finish(ConfirmContext context, TossPaymentConfirmResponse remote) {
        return finish(context, remote, false);
    }

    // FAILED 재처리 막음.
    @Transactional
    public PaymentConfirmResponse finish(ConfirmContext context, TossPaymentConfirmResponse remote, boolean rejected) {
        PaymentTarget target = targetResolver.lock(context.targetType(), context.targetId(), context.userId());
        Payment payment = findPayment(context.orderId(), context.userId());
        if (payment.getStatus() == PaymentStatus.DONE || payment.getStatus() == PaymentStatus.FAILED) {
            return target.response(payment);
        }
        validateHeldTarget(target);
        if (rejected) { payment.recordRejection(); }

        if (!matches(payment, remote)) {
            payment.markUnknown();
        } else if ("DONE".equals(remote.status()) && remote.approvedAt() != null) {
            LocalDateTime approvedAt = remote.approvedAt()
                    .atZoneSameInstant(clock.getZone()).toLocalDateTime();
            payment.complete(approvedAt, remote.method());
            target.completePayment();
        } else if (isConfirmedFailure(payment, remote)) {
            payment.markFailed();
            target.failPayment(LocalDateTime.now(clock));
        } else {
            payment.markUnknown();
        }
        if (payment.getStatus() == PaymentStatus.UNKNOWN) {
            payment.getRecovery().schedule(LocalDateTime.now(clock).plus(properties.getRecovery().getRetryDelay()));
        }
        return target.response(payment);
    }

    private Payment findPayment(String orderId, Long userId) {
        return paymentRepository.findMyPaymentWithLock(orderId, userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void validateRequest(Payment payment, PaymentTarget target, PaymentConfirmRequest request) {
        if (payment.getTargetType() != target.type()
                || !Objects.equals(payment.getTargetId(), target.id())
                || !Objects.equals(payment.getAmount(), request.amount())
                || !Objects.equals(payment.getAmount(), target.amount())
                || (payment.getPaymentKey() != null && !payment.getPaymentKey().equals(request.paymentKey()))) {
            throw new GlobalException(ErrorCode.PAYMENT_INFORMATION_MISMATCH);
        }
    }

    private void validateHeldTarget(PaymentTarget target) {
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

    private boolean isConfirmedFailure(Payment payment, TossPaymentConfirmResponse remote) {
        if (remote.approvedAt() != null) { return false; }
        return switch (remote.status() == null ? "" : remote.status()) {
            case "ABORTED", "EXPIRED" -> true;
            case "READY", "IN_PROGRESS" -> payment.isRejectedByPg();
            default -> false;
        };
    }

    private ConfirmContext context(Long userId, PaymentTarget target, Payment payment,
                                   boolean lookupOnly, PaymentConfirmResponse completed) {
        return new ConfirmContext(userId, target.type(), target.id(), payment.getOrderId(),
                payment.getPaymentKey(), payment.getAmount(), payment.getConfirmIdempotencyKey(),
                lookupOnly, completed);
    }

}
