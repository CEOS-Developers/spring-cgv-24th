package com.ceos24.springboot.payment.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.*;
import com.ceos24.springboot.payment.dto.*;
import com.ceos24.springboot.payment.repository.PaymentCancellationRepository;
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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentCancelTransactionService {
    private final PaymentRepository payments;
    private final PaymentCancellationRepository cancellations;
    private final ReservationRepository reservations;
    private final ReservationSeatRepository reservationSeats;
    private final StoreOrderRepository storeOrders;
    private final StoreOrderLifecycleService storeOrderLifecycle;

    public record CancelContext(Long userId, Long paymentId, PaymentTargetType targetType, Long targetId,
                                Long cancellationId, String paymentKey, String pgIdempotencyKey,
                                TossPaymentCancelRequest request, boolean lookupOnly,
                                PaymentCancelResponse completed) {}

    @Transactional
    public CancelContext begin(Long userId, Long paymentId, String requestId, PaymentCancelRequest request) {
        var reference = payments.findTargetById(paymentId, userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_NOT_FOUND));
        // 승인과 동일하게 구매 대상 → Payment → 취소 이력 → 재고 순서로 잠근다.
        LockedTarget target = lockTarget(reference.getTargetType(), reference.getTargetId(), userId);
        Payment payment = findPayment(paymentId, userId);
        List<PaymentCancellation> history = cancellations.findAllWithLock(paymentId);
        var existing = history.stream().filter(c -> c.getRequestId().equals(requestId)).findFirst();
        if (existing.isPresent()) {
            PaymentCancellation cancellation = existing.get();
            cancellation.validateSameRequest(request);
            return context(userId, payment, cancellation,
                    cancellation.isPending() ? null : PaymentCancelResponse.from(payment, cancellation));
        }

        payment.validateCancelable();
        target.validatePaid();
        if (history.stream().anyMatch(PaymentCancellation::isPending)) {
            throw new GlobalException(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
        }
        if ("가상계좌".equals(payment.getPaymentMethod())) {
            throw new GlobalException(ErrorCode.PAYMENT_CANCEL_METHOD_NOT_SUPPORTED);
        }
        if (request.cancelAmount() != null && request.cancelAmount() > payment.getBalanceAmount()) {
            throw new GlobalException(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED);
        }
        PaymentCancellation cancellation = cancellations.save(PaymentCancellation.start(payment, requestId, request));
        return context(userId, payment, cancellation, null);
    }

    @Transactional
    public PaymentCancelResponse finish(CancelContext context, TossPaymentCancelResponse remote, boolean rejected) {
        LockedTarget target = lockTarget(context.targetType(), context.targetId(), context.userId());
        Payment payment = findPayment(context.paymentId(), context.userId());
        List<PaymentCancellation> history = cancellations.findAllWithLock(payment.getId());
        PaymentCancellation cancellation = history.stream().filter(c -> c.getId().equals(context.cancellationId()))
                .findFirst().orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_NOT_FOUND));
        // 동시 재시도의 늦은 응답으로 완료 결과를 덮어쓰거나 잔액을 두 번 차감하지 않는다.
        if (!cancellation.isPending()) {
            return PaymentCancelResponse.from(payment, cancellation);
        }

        List<TossPaymentCancelResponse.Cancel> additions = verifiedAdditions(payment, remote, history);
        long expectedBalance = cancellation.getBalanceBefore() - cancellation.getCancelAmount();
        if (additions != null && additions.size() == 1
                && Objects.equals(payment.getBalanceAmount(), cancellation.getBalanceBefore())
                && Objects.equals(remote.balanceAmount(), expectedBalance)
                && Objects.equals(additions.getFirst().cancelAmount(), cancellation.getCancelAmount())
                && Objects.equals(additions.getFirst().cancelReason(), cancellation.getCancelReason())) {
            target.validatePaid();
            var approved = additions.getFirst();
            payment.applyCancellation(cancellation.getCancelAmount());
            cancellation.complete(approved.transactionKey(), approved.canceledAt()
                    .atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime());
            if (payment.getBalanceAmount() == 0) {
                if (target.reservation() != null) {
                    target.reservation().cancelAfterRefund();
                    reservationSeats.deleteAllByReservation_ReservationId(target.reservation().getReservationId());
                } else {
                    storeOrderLifecycle.refundLocked(target.storeOrder());
                }
            }
        } else if (rejected && additions != null && additions.isEmpty()
                && Objects.equals(remote.balanceAmount(), cancellation.getBalanceBefore())) {
            // 명시적 PG 거절 + PG 조회에서 미변경 확인이 모두 있어야 실패로 확정한다.
            cancellation.markFailed();
        } else {
            // 타임아웃, 검증 실패, 비동기 처리 중에는 좌석/재고/잔액을 유지한다.
            cancellation.markUnknown();
        }
        return PaymentCancelResponse.from(payment, cancellation);
    }

    private CancelContext context(Long userId, Payment payment, PaymentCancellation cancellation,
                                  PaymentCancelResponse completed) {
        // PG 멱등키는 15일 유효하다. 오래된 미확정 요청은 재전송하지 않고 조회만 한다.
        boolean lookupOnly = !cancellation.getCreatedAt().isAfter(LocalDateTime.now().minusDays(14));
        return new CancelContext(userId, payment.getId(), payment.getTargetType(), payment.getTargetId(),
                cancellation.getId(), payment.getPaymentKey(), cancellation.getPgIdempotencyKey(),
                new TossPaymentCancelRequest(cancellation.getCancelReason(), cancellation.getCancelAmount()),
                lookupOnly, completed);
    }

    // PG 잔액뿐 아니라 전체 취소 이력, 이전 거래 및 새 취소 거래를 함께 검증한다.
    private List<TossPaymentCancelResponse.Cancel> verifiedAdditions(
            Payment payment, TossPaymentCancelResponse remote, List<PaymentCancellation> history) {
        if (remote == null || !Objects.equals(payment.getPaymentKey(), remote.paymentKey())
                || !Objects.equals(payment.getOrderId(), remote.orderId())
                || !Objects.equals(payment.getAmount(), remote.totalAmount())
                || !Objects.equals(payment.getCurrency(), remote.currency())
                || remote.balanceAmount() == null || remote.balanceAmount() < 0
                || remote.balanceAmount() > payment.getAmount()) {
            return null;
        }
        String expectedStatus = remote.balanceAmount() == 0 ? "CANCELED"
                : remote.balanceAmount() < payment.getAmount() ? "PARTIAL_CANCELED" : "DONE";
        if (!expectedStatus.equals(remote.status())) { return null; }
        Map<String, PaymentCancellation> known = new HashMap<>();
        for (PaymentCancellation entry : history) {
            if (entry.getStatus() == PaymentCancellationStatus.DONE) {
                known.put(entry.getTransactionKey(), entry);
            }
        }
        Set<String> seen = new HashSet<>();
        List<TossPaymentCancelResponse.Cancel> additions = new ArrayList<>();
        long sum = 0;
        for (var cancel : remote.cancels() == null ? List.<TossPaymentCancelResponse.Cancel>of() : remote.cancels()) {
            if (cancel == null || !"DONE".equals(cancel.cancelStatus()) || cancel.canceledAt() == null
                    || !StringUtils.hasText(cancel.transactionKey()) || cancel.transactionKey().length() > 64
                    || !seen.add(cancel.transactionKey()) || cancel.cancelAmount() == null
                    || cancel.cancelAmount() <= 0 || cancel.cancelAmount() > payment.getAmount() - sum) {
                return null;
            }
            sum += cancel.cancelAmount();
            PaymentCancellation previous = known.get(cancel.transactionKey());
            if (previous == null) {
                additions.add(cancel);
            } else if (!Objects.equals(previous.getCancelAmount(), cancel.cancelAmount())
                    || !Objects.equals(previous.getCancelReason(), cancel.cancelReason())) {
                return null;
            }
        }
        if (sum != payment.getAmount() - remote.balanceAmount() || !seen.containsAll(known.keySet())) {
            return null;
        }
        return additions;
    }

    private Payment findPayment(Long id, Long userId) {
        return payments.findMyPaymentByIdWithLock(id, userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private LockedTarget lockTarget(PaymentTargetType type, Long id, Long userId) {
        return switch (type) {
            case RESERVATION -> new LockedTarget(reservations.findMyReservationWithLock(id, userId)
                    .orElseThrow(() -> new GlobalException(ErrorCode.RESERVATION_NOT_FOUND)), null);
            case STORE_ORDER -> new LockedTarget(null, storeOrders.findMyOrderWithLock(id, userId)
                    .orElseThrow(() -> new GlobalException(ErrorCode.STORE_ORDER_NOT_FOUND)));
        };
    }

    private record LockedTarget(Reservation reservation, StoreOrder storeOrder) {
        void validatePaid() {
            if (reservation != null ? reservation.getStatus() != ReservationStatus.Reserved || reservation.isPaymentProcessing()
                    : storeOrder.getStatus() != StoreOrderStatus.PAID || storeOrder.isPaymentProcessing()) {
                throw new GlobalException(ErrorCode.PAYMENT_NOT_CANCELABLE);
            }
        }
    }
}
