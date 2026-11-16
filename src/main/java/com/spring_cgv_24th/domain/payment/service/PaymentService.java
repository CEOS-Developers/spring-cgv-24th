package com.spring_cgv_24th.domain.payment.service;

import com.spring_cgv_24th.domain.payment.dto.PaymentCancelReqDTO;
import com.spring_cgv_24th.domain.payment.dto.PaymentPartialCancelReqDTO;
import com.spring_cgv_24th.domain.payment.dto.PaymentReqDTO;
import com.spring_cgv_24th.domain.payment.dto.PaymentResDTO;
import com.spring_cgv_24th.domain.payment.entity.Payment;
import com.spring_cgv_24th.domain.payment.entity.PaymentCancellation;
import com.spring_cgv_24th.domain.payment.enums.PaymentStatus;
import com.spring_cgv_24th.domain.payment.enums.PaymentTargetType;
import com.spring_cgv_24th.domain.payment.gateway.PaymentGateway;
import com.spring_cgv_24th.domain.payment.repository.PaymentRepository;
import com.spring_cgv_24th.domain.payment.target.PaymentTarget;
import com.spring_cgv_24th.domain.reservation.entity.Reservation;
import com.spring_cgv_24th.domain.reservation.repository.ReservationRepository;
import com.spring_cgv_24th.domain.reservation.service.ReservationService;
import com.spring_cgv_24th.domain.store.entity.StoreOrder;
import com.spring_cgv_24th.domain.store.entity.StoreOrderItem;
import com.spring_cgv_24th.domain.store.repository.StoreOrderItemRepository;
import com.spring_cgv_24th.domain.store.repository.StoreOrderRepository;
import com.spring_cgv_24th.domain.store.service.StoreService;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PaymentService {
    // 승인 요청 직후의 조회 결과로 만료를 확정하지 않도록 토스 결제 유효 시간만큼 기다린다.
    private static final long MISSING_PAYMENT_GRACE_MINUTES = 30;
    private static final String FULL_CANCELLATION = "FULL";
    private final StoreOrderRepository orderRepository;
    private final StoreOrderItemRepository itemRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final StoreService storeService;
    private final ReservationService reservationService;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final PageRequest recoveryPage;
    private long lastAttemptedPaymentId;
    private long recoveryEndPaymentId;

    public PaymentService(StoreOrderRepository orderRepository, StoreOrderItemRepository itemRepository,
                          ReservationRepository reservationRepository, PaymentRepository paymentRepository,
                          PaymentGateway gateway, StoreService storeService, ReservationService reservationService,
                          Clock clock, PlatformTransactionManager transactionManager,
                          @Value("${payment.recovery-batch-size:5}") int recoveryBatchSize) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.storeService = storeService;
        this.reservationService = reservationService;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.recoveryPage = PageRequest.ofSize(recoveryBatchSize);
    }

    public PaymentResDTO confirm(Long memberId, PaymentTargetType type, Long targetId, PaymentReqDTO request) {
        PreparedPayment prepared;
        try {
            prepared = transaction.execute(status -> prepare(memberId, type, targetId, request));
        } catch (DataIntegrityViolationException error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && violation.getConstraintName() != null
                        && (violation.getConstraintName().contains("uk_payment_target")
                        || violation.getConstraintName().contains("uk_payment_key"))) {
                    throw new CustomException(ErrorCode.PAYMENT_REQUEST_CONFLICT);
                }
            }
            throw error;
        }
        PaymentSnapshot snapshot = prepared.snapshot();
        PaymentResDTO current = snapshot.payment();
        if (current.status() == PaymentStatus.COMPLETED) return current;

        // PG 호출은 준비 트랜잭션의 잠금을 해제한 뒤 실행한다.
        PaymentResDTO result;
        if (prepared.submit()) {
            try {
                result = applyResult(snapshot, gateway.confirm(current.paymentKey(), current.paymentOrderId(),
                        current.amount(), "confirm-" + current.paymentOrderId()));
            } catch (PaymentGateway.Failure failure) {
                FailureReason reason = failure.isRejected() ? FailureReason.APPROVAL_REJECTED : FailureReason.RESULT_UNKNOWN;
                result = handleFailure(snapshot, reason);
            }
        } else {
            result = refreshPayment(snapshot);
        }
        if (result.status() == PaymentStatus.FAILED) {
            throw new CustomException(ErrorCode.PAYMENT_CONFIRMATION_FAILED);
        }
        if (result.status() != PaymentStatus.COMPLETED) {
            throw new CustomException(ErrorCode.PAYMENT_PENDING);
        }
        return result;
    }

    public PaymentResDTO getPayment(Long memberId, PaymentTargetType type, Long targetId) {
        PaymentSnapshot current = transaction.execute(status -> {
            validateOwner(findTarget(type, targetId, false), memberId);
            return PaymentSnapshot.from(requirePayment(type, targetId));
        });
        return refreshPayment(current);
    }

    public PaymentResDTO cancel(Long memberId, PaymentTargetType type, Long targetId, PaymentCancelReqDTO request) {
        return executeCancellation(transaction.execute(status ->
                prepareCancellation(memberId, type, targetId, request.cancelReason(), null)));
    }

    public PaymentResDTO cancelPartially(Long memberId, PaymentTargetType type, Long targetId,
                                        PaymentPartialCancelReqDTO request) {
        return executeCancellation(transaction.execute(status ->
                prepareCancellation(memberId, type, targetId, request.cancelReason(), request)));
    }

    private PreparedPayment prepareCancellation(Long memberId, PaymentTargetType type, Long targetId,
                                                     String reason, PaymentPartialCancelReqDTO partial) {
        PaymentTarget target = findTarget(type, targetId, true);
        validateOwner(target, memberId);
        Payment payment = requirePayment(type, targetId);
        boolean full = partial == null;
        String requestId = full ? FULL_CANCELLATION : partial.requestId().toString();
        Map<Long, Integer> items = full ? Map.of() : cancellationItems(type, partial);
        PaymentCancellation existing = payment.getCancellations().reversed().stream()
                .filter(item -> full ? item.isFullCancellation() : item.getRequestId().equals(requestId))
                .findFirst().orElse(null);

        if (existing != null && (!full || existing.getFailedAt() == null)) {
            return resumeCancellation(payment, existing, reason, items, full);
        }
        if (full && payment.getStatus() == PaymentStatus.CANCELLED) {
            return new PreparedPayment(PaymentSnapshot.from(payment), false);
        }

        String newRequestId = existing == null ? requestId : UUID.randomUUID().toString();
        return createCancellation(target, payment, newRequestId, reason, full, items);
    }

    private PreparedPayment resumeCancellation(Payment payment, PaymentCancellation existing,
                                                     String reason, Map<Long, Integer> items, boolean full) {
        if (!full
                && (!existing.getItems().equals(items) || !existing.getCancelReason().equals(reason))) {
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_REQUEST_CONFLICT);
        }
        if (existing.getFailedAt() != null) {
            throw new CustomException(ErrorCode.PAYMENT_CANCELLATION_REJECTED);
        }
        if (existing.getCompletedAt() != null) {
            return new PreparedPayment(PaymentSnapshot.from(payment), false);
        }
        if (payment.getStatus() == PaymentStatus.CANCEL_PROCESSING
                || payment.getStatus() == PaymentStatus.CANCEL_REJECTED_VERIFY) {
            throw new CustomException(ErrorCode.PAYMENT_PENDING);
        }

        // 재시도는 최초 접수 시점의 취소 사유와 검증 결과를 사용한다.
        return beginCancellation(payment, existing);
    }

    private PreparedPayment createCancellation(PaymentTarget target, Payment payment, String requestId,
                                                     String reason, boolean full, Map<Long, Integer> items) {
        // 이력 테이블 도입 전에 접수된 전체 취소는 기존 멱등키와 취소 사유를 사용한다.
        boolean legacyRetry = full && payment.getCancellations().isEmpty()
                && payment.getStatus() == PaymentStatus.CANCEL_UNKNOWN;
        if (!legacyRetry) {
            if (payment.getStatus().isCancellationPending()) {
                throw new CustomException(ErrorCode.PAYMENT_PENDING);
            }
            if (payment.getStatus() != PaymentStatus.COMPLETED
                    && payment.getStatus() != PaymentStatus.PARTIALLY_CANCELLED) {
                throw new CustomException(ErrorCode.PAYMENT_NOT_CANCELLABLE);
            }
            target.validatePaymentCancellation(LocalDateTime.now(clock));
        }

        long amount = full ? payment.getBalanceAmount() : calculatePartialCancellationAmount(target, items);
        if (amount <= 0 || amount > payment.getBalanceAmount()) {
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_INVALID);
        }

        PaymentCancellation cancellation = new PaymentCancellation(payment, requestId,
                legacyRetry ? payment.getCancelReason() : reason, amount, full, items, clock.instant());
        payment.addCancellation(cancellation);
        paymentRepository.saveAndFlush(payment);
        return beginCancellation(payment, cancellation);
    }

    private long calculatePartialCancellationAmount(PaymentTarget target, Map<Long, Integer> items) {
        if (target instanceof Reservation reservation) {
            return reservationService.calculateCancellationAmount(reservation, items);
        }
        return storeService.calculateCancellationAmount((StoreOrder) target, items);
    }

    private PreparedPayment beginCancellation(Payment payment, PaymentCancellation cancellation) {
        payment.beginCancellation(cancellation.getCancelReason());
        return new PreparedPayment(PaymentSnapshot.from(payment), true);
    }

    private Map<Long, Integer> cancellationItems(PaymentTargetType type, PaymentPartialCancelReqDTO request) {
        Map<Long, Integer> items = new TreeMap<>();
        if (type == PaymentTargetType.STORE_ORDER && request.screeningSeatIds().isEmpty()) {
            for (PaymentPartialCancelReqDTO.Item item : request.items()) {
                if (items.putIfAbsent(item.productId(), item.quantity()) != null) {
                    throw new CustomException(ErrorCode.PAYMENT_CANCEL_INVALID);
                }
            }
        } else if (type == PaymentTargetType.RESERVATION && request.items().isEmpty()) {
            for (Long seatId : request.screeningSeatIds()) {
                if (items.putIfAbsent(seatId, 1) != null) throw new CustomException(ErrorCode.PAYMENT_CANCEL_INVALID);
            }
        }
        if (items.isEmpty()) throw new CustomException(ErrorCode.PAYMENT_CANCEL_INVALID);
        return items;
    }

    private PaymentResDTO executeCancellation(PreparedPayment prepared) {
        PaymentSnapshot snapshot = prepared.snapshot();
        PaymentResDTO current = snapshot.payment();
        if (!prepared.submit()) return current;
        String key = "cancel-" + current.paymentOrderId()
                + (FULL_CANCELLATION.equals(snapshot.cancellationRequestId()) ? "" : "-" + snapshot.cancellationRequestId());

        PaymentResDTO result;
        try {
            PaymentGateway.Result response = gateway.cancel(
                    current.paymentKey(), current.cancelReason(), snapshot.cancelAmount(), key);
            result = applyResult(snapshot, response);
        } catch (PaymentGateway.Failure failure) {
            result = failure.isRejected() ? reconcileRejectedCancellation(snapshot) : markResultUnknown(snapshot);
        }
        if (result.status() != PaymentStatus.CANCELLED && result.status() != PaymentStatus.PARTIALLY_CANCELLED) {
            throw new CustomException(ErrorCode.PAYMENT_PENDING);
        }
        return result;
    }

    private PaymentResDTO reconcileRejectedCancellation(PaymentSnapshot snapshot) {
        PaymentResDTO current = snapshot.payment();
        PaymentResDTO pending = update(snapshot, (target, payment) -> payment.verifyCancellationRejection());
        PaymentResDTO result = refreshPayment(new PaymentSnapshot(pending, snapshot.cancellationId(),
                snapshot.cancellationRequestId(), snapshot.cancelAmount()));
        if (result.balanceAmount() == current.balanceAmount() && !result.status().requiresReconciliation()) {
            throw new CustomException(ErrorCode.PAYMENT_CANCELLATION_REJECTED);
        }
        return result;
    }

    public synchronized void recoverUnresolvedPayments() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<RecoveryCandidate> candidates = transaction.execute(status -> loadRecoveryBatch(now));
        for (RecoveryCandidate candidate : candidates) {
            lastAttemptedPaymentId = candidate.id();
            try {
                refreshPayment(candidate.snapshot());
            } catch (RuntimeException error) {
                log.warn("미확정 결제 재조회 실패: paymentId={}", candidate.id(), error);
            }
        }
        if (candidates.size() < recoveryPage.getPageSize() || lastAttemptedPaymentId == recoveryEndPaymentId) {
            lastAttemptedPaymentId = 0;
        }
    }

    private List<RecoveryCandidate> loadRecoveryBatch(LocalDateTime now) {
        List<Payment> candidates = lastAttemptedPaymentId == 0 ? List.of() : findRecoveryCandidates(now);
        if (candidates.isEmpty()) {
            lastAttemptedPaymentId = 0;
            // 순회 중 새 결제가 추가돼도 앞쪽 미확정 건의 재시도를 계속 미루지 않는다.
            recoveryEndPaymentId = paymentRepository.findLastPaymentId();
            candidates = findRecoveryCandidates(now);
        }
        return candidates.stream().map(RecoveryCandidate::from).toList();
    }

    private List<Payment> findRecoveryCandidates(LocalDateTime now) {
        return paymentRepository.findUnresolvedPayments(
                List.of(PaymentStatus.PROCESSING, PaymentStatus.UNKNOWN),
                List.of(PaymentStatus.CANCEL_PROCESSING, PaymentStatus.CANCEL_UNKNOWN, PaymentStatus.CANCEL_REJECTED_VERIFY),
                now, lastAttemptedPaymentId, recoveryEndPaymentId, recoveryPage);
    }

    private PaymentResDTO refreshPayment(PaymentSnapshot current) {
        if (!current.payment().status().requiresReconciliation()) return current.payment();
        try {
            return applyResult(current, gateway.find(current.payment().paymentKey()));
        } catch (PaymentGateway.Failure failure) {
            FailureReason reason = failure.isNotFound() && !current.payment().status().isCancellationPending()
                    ? FailureReason.APPROVAL_NOT_FOUND : FailureReason.RESULT_UNKNOWN;
            return handleFailure(current, reason);
        }
    }

    private boolean approvalRecoveryExpired(PaymentTarget target) {
        LocalDateTime expiresAt = target.getExpiresAt();
        return expiresAt != null && !LocalDateTime.now(clock)
                .isBefore(expiresAt.plusMinutes(MISSING_PAYMENT_GRACE_MINUTES));
    }

    private PreparedPayment prepare(Long memberId, PaymentTargetType type, Long targetId, PaymentReqDTO request) {
        PaymentTarget target = findTarget(type, targetId, true);
        validateOwner(target, memberId);
        Payment existing = paymentRepository.findByTargetTypeAndTargetId(type, targetId).orElse(null);
        long amount = existing != null ? existing.getAmount() : amountOf(target);
        if (!Objects.equals(target.getPaymentOrderId(), request.paymentOrderId())
                || (existing != null && !existing.getPaymentKey().equals(request.paymentKey()))) {
            throw new CustomException(ErrorCode.PAYMENT_REQUEST_CONFLICT);
        }
        if (amount != request.amount()) throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        if (existing != null) {
            if (existing.getStatus() == PaymentStatus.PROCESSING) throw new CustomException(ErrorCode.PAYMENT_PENDING);
            if (existing.getStatus() == PaymentStatus.FAILED) throw new CustomException(ErrorCode.PAYMENT_CONFIRMATION_FAILED);
            if (existing.getStatus() != PaymentStatus.COMPLETED && existing.getStatus() != PaymentStatus.UNKNOWN) {
                throw new CustomException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
            }
            return new PreparedPayment(PaymentSnapshot.from(existing), false);
        }
        if (paymentRepository.existsByPaymentKey(request.paymentKey())) {
            throw new CustomException(ErrorCode.PAYMENT_REQUEST_CONFLICT);
        }
        target.beginPayment(LocalDateTime.now(clock));
        Payment payment = paymentRepository.saveAndFlush(new Payment(type, target, request.paymentKey(), amount));
        return new PreparedPayment(PaymentSnapshot.from(payment), true);
    }

    private long amountOf(PaymentTarget target) {
        if (target instanceof Reservation reservation) return reservation.getTotalPrice();
        return itemRepository.findAllByOrder_IdOrderByProduct_IdAsc(target.getId()).stream()
                .mapToLong(StoreOrderItem::getTotalPrice).sum();
    }

    private PaymentResDTO applyResult(PaymentSnapshot current, PaymentGateway.Result response) {
        if (!isValidGatewayResponse(current.payment(), response)) {
            return markResultUnknown(current);
        }
        return update(current, (target, payment) -> {
            if (payment.getStatus().isCancellationPending()) {
                applyCancellationResult(target, payment, current.payment().status(), response);
            } else {
                applyApprovalResult(target, payment, response);
            }
        });
    }

    private void applyApprovalResult(PaymentTarget target, Payment payment, PaymentGateway.Result response) {
        switch (response.status()) {
            case "DONE" -> {
                payment.complete(response.method(), response.approvedAt().toInstant(), response.balanceAmount());
                target.completePayment();
            }
            case "ABORTED", "EXPIRED" -> failApproval(target, payment);
            default -> payment.markUnknown();
        }
    }

    private void applyCancellationResult(PaymentTarget target, Payment payment, PaymentStatus requestedStatus,
                                         PaymentGateway.Result response) {
        PaymentCancellation cancellation = payment.getLatestCancellation();
        long expectedBalance = cancellation == null ? 0 : cancellation.getBalanceBefore() - cancellation.getAmount();
        boolean partiallyCancelled = "PARTIAL_CANCELED".equals(response.status());
        boolean cancelled = "CANCELED".equals(response.status()) || partiallyCancelled;
        if (cancelled && response.balanceAmount() == expectedBalance) {
            Map<Long, Integer> items = cancellation == null ? Map.of() : cancellation.getItems();
            boolean fullyCancelled = expectedBalance == 0;
            if (target instanceof Reservation reservation) {
                reservationService.cancelPaidReservation(reservation, items, fullyCancelled);
            } else {
                storeService.cancelPaidOrder((StoreOrder) target, items, fullyCancelled);
            }
            payment.cancel(expectedBalance);
            if (cancellation != null) {
                cancellation.complete(clock.instant());
            }
            return;
        }

        if (requestedStatus == PaymentStatus.CANCEL_REJECTED_VERIFY
                && response.balanceAmount() == payment.getBalanceAmount()
                && ("DONE".equals(response.status()) || partiallyCancelled)) {
            cancellation.fail(clock.instant());
            payment.rejectCancellation();
            return;
        }
        payment.markUnknown();
    }

    private boolean isValidGatewayResponse(PaymentResDTO payment, PaymentGateway.Result response) {
        if (response == null || !payment.paymentKey().equals(response.paymentKey())
                || !payment.paymentOrderId().equals(response.orderId())
                || payment.amount() != response.totalAmount() || !"KRW".equals(response.currency())
                || response.status() == null || response.balanceAmount() == null
                || response.balanceAmount() < 0 || response.balanceAmount() > payment.amount()) {
            return false;
        }
        return switch (response.status()) {
            case "CANCELED" -> response.balanceAmount() == 0;
            // 여러 번 취소하면 토스가 잔액 0원에도 PARTIAL_CANCELED를 반환할 수 있다.
            case "PARTIAL_CANCELED" -> response.balanceAmount() < payment.amount();
            case "DONE" -> response.approvedAt() != null && response.method() != null;
            default -> true;
        };
    }

    private PaymentResDTO markResultUnknown(PaymentSnapshot current) {
        return handleFailure(current, FailureReason.RESULT_UNKNOWN);
    }

    private PaymentResDTO handleFailure(PaymentSnapshot current, FailureReason reason) {
        PaymentResDTO result = update(current, (target, payment) -> {
            boolean approvalFailed = switch (reason) {
                case APPROVAL_REJECTED -> true;
                case APPROVAL_NOT_FOUND -> approvalRecoveryExpired(target);
                case RESULT_UNKNOWN -> false;
            };
            if (approvalFailed) {
                failApproval(target, payment);
            } else {
                payment.markUnknown();
            }
        });
        if (!result.status().requiresReconciliation()) return result;
        throw new CustomException(ErrorCode.PAYMENT_GATEWAY_ERROR);
    }

    private void failApproval(PaymentTarget target, Payment payment) {
        payment.fail();
        target.failPayment();
    }

    private PaymentResDTO update(PaymentSnapshot snapshot, BiConsumer<PaymentTarget, Payment> change) {
        return transaction.execute(status -> {
            PaymentResDTO current = snapshot.payment();
            PaymentTarget target = findTarget(current.targetType(), current.targetId(), true);
            Payment payment = requirePayment(current.targetType(), current.targetId());
            if (payment.getStatus().requiresReconciliation()
                    && payment.getStatus().isCancellationPending() == current.status().isCancellationPending()) {
                PaymentCancellation cancellation = payment.getLatestCancellation();
                Long cancellationId = cancellation == null ? null : cancellation.getId();
                if (Objects.equals(cancellationId, snapshot.cancellationId())) {
                    change.accept(target, payment);
                }
            }
            return PaymentResDTO.from(payment);
        });
    }

    private Payment requirePayment(PaymentTargetType type, Long targetId) {
        return paymentRepository.findByTargetTypeAndTargetId(type, targetId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private PaymentTarget findTarget(PaymentTargetType type, Long id, boolean lock) {
        return switch (type) {
            case STORE_ORDER -> (lock ? orderRepository.findByIdForUpdate(id) : orderRepository.findById(id))
                    .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_TARGET_NOT_FOUND));
            case RESERVATION -> (lock ? reservationRepository.findByIdForUpdate(id) : reservationRepository.findById(id))
                    .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_TARGET_NOT_FOUND));
        };
    }

    private void validateOwner(PaymentTarget target, Long memberId) {
        if (!Objects.equals(target.getMember().getId(), memberId)) throw new CustomException(ErrorCode.PAYMENT_FORBIDDEN);
    }

    private enum FailureReason {
        APPROVAL_REJECTED,
        APPROVAL_NOT_FOUND,
        RESULT_UNKNOWN
    }

    private record PaymentSnapshot(PaymentResDTO payment, Long cancellationId, String cancellationRequestId,
                                   Long cancelAmount) {
        static PaymentSnapshot from(Payment payment) {
            PaymentCancellation cancellation = payment.getLatestCancellation();
            return new PaymentSnapshot(PaymentResDTO.from(payment),
                    cancellation == null ? null : cancellation.getId(),
                    cancellation == null ? null : cancellation.getRequestId(),
                    cancellation == null || cancellation.isFullCancellation() ? null : cancellation.getAmount());
        }
    }

    private record PreparedPayment(PaymentSnapshot snapshot, boolean submit) {
    }

    private record RecoveryCandidate(long id, PaymentSnapshot snapshot) {
        static RecoveryCandidate from(Payment payment) {
            return new RecoveryCandidate(payment.getId(), PaymentSnapshot.from(payment));
        }
    }
}
