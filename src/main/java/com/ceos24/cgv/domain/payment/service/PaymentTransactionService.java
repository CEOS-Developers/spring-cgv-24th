package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentProperties;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentCommandResponse;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.*;
import com.ceos24.cgv.domain.payment.repository.PaymentOperationRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.payment.service.target.PaymentTargetHandler;
import com.ceos24.cgv.domain.payment.service.target.PaymentTargetHandlers;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;
import com.ceos24.cgv.domain.store.repository.PurchaseRepository;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
public class PaymentTransactionService {

    private final PurchaseRepository purchases;
    private final PaymentRepository payments;
    private final PaymentOperationRepository operations;
    private final UserRepository users;
    private final PaymentTargetHandlers targetHandlers;
    private final TossPaymentProperties properties;

    @PersistenceContext
    private EntityManager entityManager;

    public Preparation prepareConfirm(String username, PaymentConfirmRequest request, String key) {
        freshContext();
        validateKey(key);
        Payment payment = findPaymentForConfirmation(username, request.orderId());
        requireOwner(username, payment.getPayer().getId());
        PaymentTargetHandler handler = targetHandlers.get(payment.getTargetType());
        PaymentOperation existing = operations.findByPayment_IdAndTypeAndIdempotencyKey(
                payment.getId(), PaymentOperationType.CONFIRM, key).orElse(null);
        if (existing != null) {
            if (!existing.matchesRequest(PaymentOperationType.CONFIRM, request.paymentKey(), request.amount(), null)) {
                throw new BusinessException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
            }
            return resume(payment, existing);
        }
        requireNoOtherWork(payment, null);
        if (request.amount() != payment.getTotalAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        if (payment.getStatus() != PaymentStatus.READY || !handler.isPayable(payment)) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }
        if (!LocalDateTime.now().isBefore(payment.getExpiresAt())) {
            handler.afterExpiration(payment);
            payment.expire();
            // 예외를 트랜잭션 밖에서 반환하여 만료·재고 복구가 롤백되지 않게 한다.
            return Preparation.rejected(ErrorCode.PAYMENT_ORDER_EXPIRED);
        }
        Payment other = payments.findByPaymentKey(request.paymentKey()).orElse(null);
        if (other != null && !other.getId().equals(payment.getId())) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }
        PaymentOperation operation = operations.save(PaymentOperation.startConfirm(payment, request.paymentKey(), key));
        return Preparation.started(work(operation, false));
    }

    public Preparation prepareCancel(String username, Long paymentId, PaymentCancelRequest request, String key) {
        freshContext();
        validateKey(key);
        Payment payment = lockPayment(paymentId, username);
        requireOwner(username, payment.getPayer().getId());
        PaymentOperation existing = operations.findByPayment_IdAndTypeAndIdempotencyKey(
                paymentId, PaymentOperationType.CANCEL, key).orElse(null);
        if (existing != null) {
            if (!existing.matchesCancelRequest(request.cancelAmount(), request.cancelReason())) {
                throw new BusinessException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
            }
            return resume(payment, existing);
        }
        requireNoOtherWork(payment, null);
        if (payment.getStatus() != PaymentStatus.DONE && payment.getStatus() != PaymentStatus.PARTIAL_CANCELED) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }
        long amount = request.cancelAmount() == null ? payment.getRemainingAmount() : request.cancelAmount();
        if (amount <= 0 || amount > payment.getRemainingAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_CANCEL_AMOUNT);
        }
        targetHandlers.get(payment.getTargetType()).validateCancellation(payment, amount);
        PaymentOperation operation = operations.save(request.cancelAmount() == null
                ? PaymentOperation.startFullCancel(payment, request.cancelReason(), key)
                : PaymentOperation.startCancel(payment, amount, request.cancelReason(), key));
        return Preparation.started(work(operation, false));
    }

    private Preparation resume(Payment payment, PaymentOperation operation) {
        if (operation.getStatus() == PaymentOperationStatus.SUCCEEDED) {
            return Preparation.replayed(receipt(payment, operation));
        }
        if (operation.getStatus() == PaymentOperationStatus.FAILED) {
            return Preparation.rejected(ErrorCode.PAYMENT_OPERATION_FAILED);
        }
        requireNoOtherWork(payment, operation.getId());
        // 복구 시 조회와 재승인이 연속 실행될 수 있으므로 HTTP 두 번의 제한시간을 확보한다.
        Duration lease = properties.connectTimeout().plus(properties.readTimeout()).multipliedBy(2).plusSeconds(30);
        if (operation.getStatus() == PaymentOperationStatus.PROCESSING
                && operation.getLastAttemptAt().plus(lease).isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.PAYMENT_OPERATION_IN_PROGRESS);
        }
        operation.retry();
        return Preparation.started(work(operation, true));
    }

    public PaymentCommandResponse complete(Work work, TossPaymentResponse response) {
        freshContext();
        PaymentOperation operation = lockOperation(work.operationId());
        Payment payment = operation.getPayment();
        if (operation.getStatus() == PaymentOperationStatus.SUCCEEDED) {
            return receipt(payment, operation);
        }
        if (!operation.isUnresolved()) {
            throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
        }
        validateIdentity(operation, response);
        if (operation.getType() == PaymentOperationType.CONFIRM) {
            if (!"DONE".equals(response.status()) || response.balanceAmount() != payment.getTotalAmount()
                    || response.approvedAt() == null || response.method() == null || response.method().isBlank()) {
                throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
            }
            validateTransactionKey(response.lastTransactionKey(), operation);
            payment.approve(response.paymentKey(), response.method(), response.approvedAt().toInstant());
            targetHandlers.get(payment.getTargetType()).afterApproval(payment);
        } else {
            long expectedCanceled = Math.addExact(operation.getBeforeCanceledAmount(), operation.getAmount());
            String expectedStatus = expectedCanceled == payment.getTotalAmount() ? "CANCELED" : "PARTIAL_CANCELED";
            boolean transactionMatches = response.lastTransactionKey() != null && response.cancels().stream()
                    .anyMatch(cancel -> response.lastTransactionKey().equals(cancel.transactionKey())
                            && cancel.cancelAmount() != null && cancel.cancelAmount() == operation.getAmount()
                            && "DONE".equals(cancel.cancelStatus()));
            if (payment.getCanceledAmount() != operation.getBeforeCanceledAmount()
                    || response.balanceAmount() != payment.getTotalAmount() - expectedCanceled
                    || !expectedStatus.equals(response.status()) || !transactionMatches) {
                throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
            }
            validateTransactionKey(response.lastTransactionKey(), operation);
            payment.cancel(operation.getAmount());
            if (payment.getStatus() == PaymentStatus.CANCELED) {
                targetHandlers.get(payment.getTargetType()).afterFullCancellation(payment);
            } else {
                targetHandlers.get(payment.getTargetType()).afterPartialCancellation(payment);
            }
        }
        operation.succeed(response.lastTransactionKey());
        operation.captureResult();
        return receipt(payment, operation);
    }

    // 다른 실행이 먼저 완료했다면 그 결과를 반환한다. 이전 시도의 오류로 새 시도를 덮어쓰지 않는다.
    public PaymentCommandResponse recordError(Work work, boolean definite, String code, String message) {
        freshContext();
        PaymentOperation operation = lockOperation(work.operationId());
        Payment payment = operation.getPayment();
        if (operation.getStatus() == PaymentOperationStatus.SUCCEEDED) {
            return receipt(payment, operation);
        }
        if (!operation.isUnresolved() || operation.getAttemptNumber() != work.attemptNumber()) {
            return null;
        }
        String errorCode = truncate(code == null || code.isBlank() ? "PAYMENT_RESULT_UNKNOWN" : code, 100);
        String errorMessage = message == null ? null : truncate(message, 1000);
        if (definite) {
            operation.fail(errorCode, errorMessage);
            if (operation.getType() == PaymentOperationType.CONFIRM) {
                targetHandlers.get(payment.getTargetType()).afterConfirmationRejected(payment);
                payment.abort();
            }
        } else {
            operation.markUnknown(errorCode, errorMessage);
        }
        return null;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public PaymentResponse getPayment(String username, Long paymentId) {
        freshContext();
        Payment payment = payments.findWithTargetById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        requireOwner(username, payment.getPayer().getId());
        return PaymentResponse.from(payment, operations.findAllByPayment_IdOrderByRequestedAtAscIdAsc(paymentId));
    }

    // 만료 후보도 같은 잠금 순서로 재검증한다. PG 결과가 불명확한 작업은 시간만으로 해제하지 않는다.
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public boolean expireUnprocessedReservation(Long paymentId, LocalDateTime now) {
        // 좌석 요청의 트랜잭션에 참여한다. 기존 변경을 clear로 분리하거나 별도 DB 연결을 잡지 않는다.
        Payment payment = lockPayment(paymentId);
        if (payment.getTargetType() != PaymentTargetType.RESERVATION || payment.getStatus() != PaymentStatus.READY
                || now.isBefore(payment.getExpiresAt())) return false;
        if (!operations.findAllByPaymentIdAndStatusInForUpdate(paymentId,
                List.of(PaymentOperationStatus.PROCESSING, PaymentOperationStatus.UNKNOWN)).isEmpty()) return false;
        PaymentTargetHandler handler = targetHandlers.get(payment.getTargetType());
        if (!handler.isPayable(payment)) return false;
        handler.afterExpiration(payment);
        payment.expire();
        return true;
    }

    private Payment findPaymentForConfirmation(String username, String orderId) {
        Long paymentId = payments.findIdByOrderId(orderId).orElse(null);
        if (paymentId != null) return lockPayment(paymentId, username);

        // 이전 단계에서 Payment 없이 생성된 매점 주문만 지원하는 호환 경로다.
        // 이후 신규 예매는 결제 대기 예매 생성과 같은 트랜잭션에서 Payment를 저장한다.
        Purchase purchase = purchases.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_NOT_FOUND));
        requireOwner(username, purchase.getUserEntity().getId());
        Payment existing = payments.findByPurchaseIdForUpdate(purchase.getId()).orElse(null);
        if (existing != null) return existing;
        if (purchase.getStatus() != PurchaseStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }
        return payments.save(Payment.create(purchase));
    }

    private Payment lockPayment(Long paymentId) {
        return lockPayment(paymentId, null);
    }

    private Payment lockPayment(Long paymentId, String username) {
        // 식별자만 먼저 읽고 업무 대상 → 결제 → 작업 순으로 잠근다.
        var target = payments.findTargetById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        // 미등록 업무 종류여도 먼저 소유자를 확인해 타인의 요청에는 동일하게 403을 반환한다.
        if (username != null) requireOwner(username, target.payerId());
        targetHandlers.get(target.type()).lockTarget(target.targetId());
        return payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private PaymentOperation lockOperation(Long operationId) {
        Long paymentId = operations.findPaymentIdById(operationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        lockPayment(paymentId);
        return operations.findByIdForUpdate(operationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void requireNoOtherWork(Payment payment, Long excludedId) {
        boolean blocked = operations.findAllByPaymentIdAndStatusInForUpdate(payment.getId(),
                        List.of(PaymentOperationStatus.PROCESSING, PaymentOperationStatus.UNKNOWN))
                .stream().anyMatch(operation -> !Objects.equals(excludedId, operation.getId()));
        if (blocked) {
            throw new BusinessException(ErrorCode.PAYMENT_OPERATION_IN_PROGRESS);
        }
    }

    private void requireOwner(String username, Long payerId) {
        Long userId = users.findByUsernameAndIsLockAndIsSocial(username, false, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)).getId();
        if (!userId.equals(payerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
    }

    private void validateIdentity(PaymentOperation operation, TossPaymentResponse response) {
        if (response == null || !operation.getPaymentKey().equals(response.paymentKey())
                || !operation.getPayment().getOrderId().equals(response.orderId())
                || response.totalAmount() == null || response.totalAmount() != operation.getPayment().getTotalAmount()
                || response.balanceAmount() == null || !"KRW".equals(response.currency())) {
            throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
        }
    }

    private void validateTransactionKey(String key, PaymentOperation operation) {
        if (key != null && (key.isBlank() || key.length() > 64
                || operations.existsByPgTransactionKeyAndIdNot(key, operation.getId()))) {
            throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
        }
    }

    private PaymentCommandResponse receipt(Payment payment, PaymentOperation operation) {
        if (operation.getResultPaymentStatus() == null || operation.getResultCanceledAmount() == null) {
            throw new BusinessException(ErrorCode.PAYMENT_MANUAL_REVIEW_REQUIRED);
        }
        return PaymentCommandResponse.from(payment, operation);
    }

    private Work work(PaymentOperation operation, boolean recovery) {
        return new Work(operation.getId(), operation.getPayment().getId(), operation.getType(),
                operation.getPaymentKey(), operation.getPayment().getOrderId(), operation.getAmount(),
                operation.getCancelReason(), operation.getPgIdempotencyKey(), operation.getRequestedAt(),
                operation.getAttemptNumber(), recovery);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 300
                || key.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_IDEMPOTENCY_KEY);
        }
    }

    private void freshContext() {
        // OSIV가 켜져 있어도 PG 호출 전 트랜잭션의 캐시를 결과 반영 시 재사용하지 않는다.
        entityManager.clear();
    }

    private String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    public record Work(Long operationId, Long paymentId, PaymentOperationType type, String paymentKey,
                       String orderId, long amount, String cancelReason, String pgIdempotencyKey,
                       Instant requestedAt, int attemptNumber, boolean recovery) {
    }

    public record Preparation(Work work, PaymentCommandResponse replay, ErrorCode rejection) {
        static Preparation started(Work work) { return new Preparation(work, null, null); }
        static Preparation replayed(PaymentCommandResponse response) { return new Preparation(null, response, null); }
        static Preparation rejected(ErrorCode code) { return new Preparation(null, null, code); }
    }
}
