package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.order.entity.Order;
import com.ceos24.cgv.domain.order.entity.OrderItem;
import com.ceos24.cgv.domain.order.repository.OrderRepository;
import com.ceos24.cgv.domain.payment.dto.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareResponse;
import com.ceos24.cgv.domain.payment.dto.PaymentResponse;
import com.ceos24.cgv.domain.payment.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancelHistory;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentCancelHistoryRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.store.entity.MenuStock;
import com.ceos24.cgv.domain.store.repository.MenuStockRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final PaymentCancelHistoryRepository paymentCancelHistoryRepository;
    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final MenuStockRepository menuStockRepository;

    @Transactional
    public PaymentPrepareResponse preparePayment(Long memberId, PaymentPrepareRequest request) {
        if (request.reservationId() != null) {
            return prepareReservationPayment(memberId, request.reservationId());
        }
        return prepareOrderPayment(memberId, request.orderId());
    }

    private PaymentPrepareResponse prepareOrderPayment(Long memberId, Long orderId) {
        Order order = orderRepository
                .findByIdWithLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN_USER, "해당 주문에 대한 결제 권한이 없습니다.");
        }

        List<Payment> existingPayments = paymentRepository.findAllByOrderOrderByIdDescWithLock(order);
        Optional<Payment> latestPayment = existingPayments.stream().findFirst();
        if (latestPayment.isPresent()) {
            Payment p = latestPayment.get();
            if (p.getStatus() == PaymentStatus.DONE
                    || p.getStatus() == PaymentStatus.PARTIAL_CANCELED
                    || p.getStatus() == PaymentStatus.CANCELED
                    || p.getStatus() == PaymentStatus.MANUAL_RECOVERY_REQUIRED) {
                throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED, "이미 결제가 처리된 주문입니다.");
            }
            if (p.getStatus() == PaymentStatus.IN_PROGRESS) {
                throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
            }
            if (p.getStatus() == PaymentStatus.READY) {
                if (p.getTotalAmount().equals(order.getTotalPrice())) {
                    return new PaymentPrepareResponse(
                            p.getOrderId(), p.getTotalAmount(), createOrderName(order), p.getId());
                }
                p.abort();
            }
        }

        String orderCode = "CGV_ORDER_"
                + order.getId()
                + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Payment payment = Payment.createReady(order, orderCode, order.getTotalPrice());
        paymentRepository.save(payment);

        return new PaymentPrepareResponse(
                payment.getOrderId(), payment.getTotalAmount(), createOrderName(order), payment.getId());
    }

    private PaymentPrepareResponse prepareReservationPayment(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository
                .findByIdWithLock(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }

        reservation.validatePayable(LocalDateTime.now());

        List<Payment> existingPayments = paymentRepository.findAllByReservationOrderByIdDescWithLock(reservation);
        Optional<Payment> latestPayment = existingPayments.stream().findFirst();
        if (latestPayment.isPresent()) {
            Payment p = latestPayment.get();
            if (p.getStatus() == PaymentStatus.DONE
                    || p.getStatus() == PaymentStatus.PARTIAL_CANCELED
                    || p.getStatus() == PaymentStatus.CANCELED
                    || p.getStatus() == PaymentStatus.MANUAL_RECOVERY_REQUIRED) {
                throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED, "이미 결제가 처리된 예매입니다.");
            }
            if (p.getStatus() == PaymentStatus.IN_PROGRESS) {
                throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
            }
            if (p.getStatus() == PaymentStatus.READY) {
                if (p.getTotalAmount().equals(reservation.getTotalPrice())) {
                    return new PaymentPrepareResponse(
                            p.getOrderId(), p.getTotalAmount(), createReservationName(reservation), p.getId());
                }
                p.abort();
            }
        }

        String orderCode = "CGV_RES_"
                + reservation.getId()
                + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Payment payment = Payment.createReady(reservation, orderCode, reservation.getTotalPrice());
        paymentRepository.save(payment);

        return new PaymentPrepareResponse(
                payment.getOrderId(), payment.getTotalAmount(), createReservationName(reservation), payment.getId());
    }

    @Transactional
    public ConfirmStartResult startConfirm(Long memberId, PaymentConfirmRequest request) {
        Payment payment = findPaymentLockedInOrderByOrderId(request.orderId());
        payment.validateOwner(memberId);

        if (payment.getStatus() == PaymentStatus.DONE) {
            if (!payment.getTotalAmount().equals(request.amount())
                    || (payment.getPaymentKey() != null
                            && !payment.getPaymentKey().equals(request.paymentKey()))) {
                log.error(
                        "승인 완료된 결제에 대한 파라미터 불일치 요청: orderId={}, DB amount={}, req amount={}, DB key={}, req key={}",
                        request.orderId(),
                        payment.getTotalAmount(),
                        request.amount(),
                        payment.getPaymentKey(),
                        request.paymentKey());
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "이미 승인 완료된 결제의 정보와 일치하지 않습니다.");
            }
            log.info("결제 승인 멱등 응답: 이미 승인 완료된 요청 (orderId={})", request.orderId());
            return ConfirmStartResult.completed(PaymentResponse.from(payment));
        }

        if (payment.getStatus() == PaymentStatus.IN_PROGRESS) {
            if (!payment.getTotalAmount().equals(request.amount())) {
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            }
            if (payment.getPaymentKey() != null && !payment.getPaymentKey().equals(request.paymentKey())) {
                throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS, "다른 결제 요청이 승인 진행 중입니다.");
            }
            String recoveryKey = payment.getPaymentKey() != null ? payment.getPaymentKey() : request.paymentKey();
            return ConfirmStartResult.recover(recoveryKey, payment.getOrderId(), payment.getTotalAmount());
        }

        if (payment.getStatus() != PaymentStatus.READY) {
            log.warn("결제 승인 거절: 승인 가능한 상태가 아님. orderId={}, status={}", request.orderId(), payment.getStatus());
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        if (!payment.getTotalAmount().equals(request.amount())) {
            log.error(
                    "결제 금액 불일치: DB 금액={}원, 요청 금액={}원 (orderId={})",
                    payment.getTotalAmount(),
                    request.amount(),
                    request.orderId());
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        payment.validateTargetAmountMatched();
        if (payment.isReservationPayment()) {
            payment.getReservation().validatePayable(LocalDateTime.now());
        }

        payment.startConfirm(request.paymentKey());
        paymentRepository.flush();

        return ConfirmStartResult.readyToConfirm(request.paymentKey(), request.orderId(), request.amount());
    }

    @Transactional
    public CompleteResult completeApprovedPayment(TossPaymentResponse tossResponse) {
        Payment payment = findPaymentLockedInOrderByOrderId(tossResponse.orderId());

        if (payment.isApprovedOrCanceled()) {
            return new CompleteResult(false, PaymentResponse.from(payment));
        }

        if (payment.getStatus() == PaymentStatus.ABORTED
                || payment.getStatus() == PaymentStatus.MANUAL_RECOVERY_REQUIRED) {
            log.warn(
                    "이미 중단 또는 수동 확인 상태로 종료된 결제입니다. 재확정하지 않습니다. orderId={}, status={}",
                    payment.getOrderId(),
                    payment.getStatus());
            return new CompleteResult(true, PaymentResponse.from(payment));
        }

        tossResponse.validateIntegrity(payment.getOrderId(), payment.getTotalAmount());
        payment.validateTargetAmountMatched();

        if (payment.isReservationPayment()) {
            Reservation reservation = payment.getReservation();
            reservation.confirm(LocalDateTime.now());
            reservationRepository.saveAndFlush(reservation);
        }

        LocalDateTime approvedAt = parseTossDateTime(tossResponse.approvedAt());
        payment.complete(tossResponse.paymentKey(), tossResponse.method(), approvedAt);
        paymentRepository.saveAndFlush(payment);

        return new CompleteResult(false, PaymentResponse.from(payment));
    }

    @Transactional
    public void recordAbortByOrderId(String orderId) {
        Payment lookup = paymentRepository.findByOrderId(orderId).orElse(null);
        if (lookup == null) {
            return;
        }
        lockTarget(lookup);
        paymentRepository.findByOrderIdWithLock(orderId).ifPresent(payment -> {
            payment.abort();
            log.info("결제 상태를 ABORTED로 기록 완료 (orderId={})", orderId);
        });
    }

    @Transactional
    public void recordCompensatedAbort(String orderId, String paymentKey) {
        Payment lookup = paymentRepository.findByOrderId(orderId).orElse(null);
        if (lookup == null) {
            return;
        }
        lockTarget(lookup);
        paymentRepository.findByOrderIdWithLock(orderId).ifPresent(payment -> {
            payment.abort(paymentKey);
            log.info("자동 보상 취소 후 결제 상태를 ABORTED로 기록 완료 (orderId={}, paymentKey={})", orderId, paymentKey);
        });
    }

    @Transactional
    public void recordManualRecoveryRequired(String orderId, String paymentKey) {
        Payment lookup = paymentRepository.findByOrderId(orderId).orElse(null);
        if (lookup == null) {
            return;
        }
        lockTarget(lookup);
        paymentRepository.findByOrderIdWithLock(orderId).ifPresent(payment -> {
            payment.markManualRecoveryRequired(paymentKey);
            log.error("보상 취소 실패로 결제 상태를 MANUAL_RECOVERY_REQUIRED로 기록 (orderId={}, paymentKey={})", orderId, paymentKey);
        });
    }

    @Transactional
    public void recordManualRecoveryRequiredByPaymentKey(String paymentKey) {
        Payment lookup = paymentRepository.findByPaymentKey(paymentKey).orElse(null);
        if (lookup == null) {
            return;
        }
        lockTarget(lookup);
        paymentRepository.findByOrderIdWithLock(lookup.getOrderId()).ifPresent(payment -> {
            payment.markManualRecoveryRequired(paymentKey);
            log.error("후속 처리 실패로 결제 상태를 MANUAL_RECOVERY_REQUIRED로 기록 (paymentKey={})", paymentKey);
        });
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long memberId, String paymentKey) {
        Payment payment = paymentRepository
                .findByPaymentKey(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.validateOwner(memberId);
        return PaymentResponse.from(payment);
    }

    @Transactional
    public CancelPlan prepareCancel(Long memberId, String paymentKey, PaymentCancelRequest request) {
        Payment payment = findPaymentLockedInOrderByPaymentKey(paymentKey);
        payment.validateOwner(memberId);

        String normalizedCancelRequestId = normalizeBlankToNull(request.cancelRequestId());
        if (normalizedCancelRequestId != null) {
            Optional<PaymentCancelHistory> existingCancel =
                    paymentCancelHistoryRepository.findByCancelRequestId(normalizedCancelRequestId);
            if (existingCancel.isPresent()) {
                log.info("결제 취소 멱등 응답: 이미 처리된 취소 요청 (cancelRequestId={})", normalizedCancelRequestId);
                return CancelPlan.alreadyCanceled(PaymentResponse.from(payment));
            }
        }

        if (payment.getStatus() == PaymentStatus.CANCELED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CANCELED);
        }
        if (payment.getStatus() != PaymentStatus.DONE && payment.getStatus() != PaymentStatus.PARTIAL_CANCELED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED, "승인 완료된 결제만 취소할 수 있습니다.");
        }

        Long targetOrderItemId = null;
        Long cancelQty = null;
        String itemName = "전체 취소";
        Long cancelAmount;
        List<Long> sortedSeatNumbers = null;
        boolean cancelAllOrderItems = false;

        if (payment.isReservationPayment()) {
            if (request.orderItemId() != null || request.cancelQuantity() != null) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT_VALUE, "영화 예매 결제 취소에서는 주문 품목 ID 또는 수량을 지정할 수 없습니다.");
            }
            Reservation reservation = payment.getReservation();
            if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
                throw new BusinessException(ErrorCode.RESERVATION_ALREADY_PROCESSED);
            }

            if (request.seatNumbers() != null && !request.seatNumbers().isEmpty()) {
                if (request.seatNumbers().stream().anyMatch(Objects::isNull)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "좌석 번호 목록에 null이 포함될 수 없습니다.");
                }
                sortedSeatNumbers =
                        request.seatNumbers().stream().distinct().sorted().toList();
                Set<Long> reservedSeats = new HashSet<>(reservation.getSeatNumbers());
                if (!reservedSeats.containsAll(sortedSeatNumbers)) {
                    throw new BusinessException(ErrorCode.RESERVATION_SEAT_NOT_FOUND);
                }

                cancelAmount = reservation.getTicketPrice() * sortedSeatNumbers.size();
                if (request.cancelAmount() != null && !request.cancelAmount().equals(cancelAmount)) {
                    throw new BusinessException(
                            ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                            "요청한 취소 금액(" + request.cancelAmount() + "원)이 취소 대상 좌석 금액(" + cancelAmount
                                    + "원)과 일치하지 않습니다.");
                }
                cancelQty = (long) sortedSeatNumbers.size();
                itemName = "좌석 예매 취소 " + sortedSeatNumbers;
            } else {
                if (request.cancelAmount() != null && !request.cancelAmount().equals(payment.getBalanceAmount())) {
                    throw new BusinessException(
                            ErrorCode.INVALID_INPUT_VALUE,
                            "예매 부분 취소는 취소할 좌석 번호(seatNumbers)를 함께 지정해야 하며, 전체 취소 시 남은 결제 잔액("
                                    + payment.getBalanceAmount() + "원)과 일치해야 합니다.");
                }
                List<Long> remainingSeats = reservation.getSeatNumbers();
                cancelAmount = reservation.getTicketPrice() * remainingSeats.size();
                if (!cancelAmount.equals(payment.getBalanceAmount())) {
                    throw new BusinessException(
                            ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                            "예매 잔여 금액(" + cancelAmount + "원)이 결제 잔액(" + payment.getBalanceAmount() + "원)과 일치하지 않습니다.");
                }
                cancelQty = (long) remainingSeats.size();
                itemName = "좌석 예매 전체 취소 " + remainingSeats;
            }
        } else {
            if (request.seatNumbers() != null && !request.seatNumbers().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "스토어 주문 결제 취소에서는 좌석 번호를 지정할 수 없습니다.");
            }
            if (request.orderItemId() != null) {
                OrderItem targetItem = payment.getOrder().getOrderItems().stream()
                        .filter(oi -> oi.getId().equals(request.orderItemId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND));

                cancelQty = request.cancelQuantity() != null ? request.cancelQuantity() : 1L;
                if (cancelQty <= 0) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "취소 수량은 1개 이상이어야 합니다.");
                }
                if (cancelQty > targetItem.getCancelableQuantity()) {
                    throw new BusinessException(
                            ErrorCode.ORDER_ITEM_CANCEL_QUANTITY_EXCEEDED,
                            "취소 요청 수량(" + cancelQty + "개)이 남은 취소 가능 수량(" + targetItem.getCancelableQuantity()
                                    + "개)을 초과했습니다.");
                }

                targetOrderItemId = targetItem.getId();
                itemName = targetItem.getMenu().getName();
                cancelAmount = cancelQty * targetItem.getPrice();
                if (request.cancelAmount() != null && !request.cancelAmount().equals(cancelAmount)) {
                    throw new BusinessException(
                            ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                            "요청한 취소 금액(" + request.cancelAmount() + "원)이 품목 취소 금액(" + cancelAmount + "원)과 일치하지 않습니다.");
                }
            } else {
                cancelAmount = request.cancelAmount() != null ? request.cancelAmount() : payment.getBalanceAmount();
                cancelAllOrderItems = cancelAmount.equals(payment.getBalanceAmount());
            }
        }

        if (cancelAmount == null || cancelAmount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "취소 요청 금액은 0보다 커야 합니다.");
        }
        if (cancelAmount > payment.getBalanceAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED);
        }

        String effectiveIdempotencyKey = normalizedCancelRequestId != null
                ? normalizedCancelRequestId
                : UUID.randomUUID().toString();

        return CancelPlan.proceed(
                payment.getOrderId(),
                cancelAmount,
                effectiveIdempotencyKey,
                normalizedCancelRequestId,
                targetOrderItemId,
                cancelQty,
                itemName,
                sortedSeatNumbers,
                cancelAllOrderItems);
    }

    @Transactional
    public PaymentResponse applyCancel(
            Long memberId,
            String paymentKey,
            PaymentCancelRequest request,
            CancelPlan plan,
            String transactionKey,
            LocalDateTime canceledAt) {
        Payment payment = findPaymentLockedInOrderByPaymentKey(paymentKey);
        payment.validateOwner(memberId);

        if (plan.normalizedCancelRequestId() != null
                && paymentCancelHistoryRepository
                        .findByCancelRequestId(plan.normalizedCancelRequestId())
                        .isPresent()) {
            log.info("결제 취소 멱등 응답(후처리): cancelRequestId={}", plan.normalizedCancelRequestId());
            return PaymentResponse.from(payment);
        }

        String normalizedTransactionKey = normalizeBlankToNull(transactionKey);
        if (normalizedTransactionKey != null
                && paymentCancelHistoryRepository.existsByTransactionKey(normalizedTransactionKey)) {
            log.info("결제 취소 멱등 응답(후처리): 이미 반영된 transactionKey={}", normalizedTransactionKey);
            return PaymentResponse.from(payment);
        }

        OrderItem targetItem = null;
        if (payment.isReservationPayment()) {
            Reservation reservation = payment.getReservation();
            Long actualCanceledAmount = reservation.cancelByPayment(plan.sortedSeatNumbers());
            if (!actualCanceledAmount.equals(plan.cancelAmount())) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                        "예매 취소 금액(" + actualCanceledAmount + "원)이 PG 취소 금액(" + plan.cancelAmount() + "원)과 일치하지 않습니다.");
            }
        } else {
            if (plan.orderItemId() != null) {
                targetItem = payment.getOrder().getOrderItems().stream()
                        .filter(oi -> oi.getId().equals(plan.orderItemId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND));
                targetItem.cancelQuantity(plan.cancelQuantity());
                restoreSingleMenuStock(payment.getOrder(), targetItem, plan.cancelQuantity());
            } else if (plan.cancelAllOrderItems()) {
                restoreRemainingOrderItemsAndStocks(payment.getOrder());
            }
        }

        payment.cancel(
                plan.normalizedCancelRequestId(),
                normalizedTransactionKey,
                targetItem,
                plan.cancelQuantity(),
                plan.itemName(),
                plan.cancelAmount(),
                request.cancelReason(),
                canceledAt != null ? canceledAt : LocalDateTime.now());
        paymentRepository.saveAndFlush(payment);

        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public String resolvePaymentKeyByOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        return paymentRepository
                .findByOrderId(orderId)
                .map(Payment::getPaymentKey)
                .orElse(null);
    }

    @Transactional
    public void abortPendingPaymentFromWebhook(String orderId, String paymentKey) {
        Payment lookup = findLookupByOrderIdOrPaymentKey(orderId, paymentKey);
        if (lookup == null) {
            return;
        }
        lockTarget(lookup);
        paymentRepository.findByOrderIdWithLock(lookup.getOrderId()).ifPresent(payment -> {
            payment.abort(paymentKey);
            log.info("웹훅 수신으로 미완료 결제를 ABORTED 처리 (orderId={}, paymentKey={})", payment.getOrderId(), paymentKey);
        });
    }

    @Transactional
    public void applyCancelWebhook(TossPaymentResponse latestTossPayment) {
        Payment lookup = findLookupByOrderIdOrPaymentKey(latestTossPayment.orderId(), latestTossPayment.paymentKey());
        if (lookup == null) {
            log.warn(
                    "웹훅 취소 동기화 대상 결제를 찾을 수 없습니다. orderId={}, paymentKey={}",
                    latestTossPayment.orderId(),
                    latestTossPayment.paymentKey());
            return;
        }

        lockTarget(lookup);
        Payment payment =
                paymentRepository.findByOrderIdWithLock(lookup.getOrderId()).orElse(null);
        if (payment == null) {
            return;
        }

        if (payment.getStatus() == PaymentStatus.CANCELED
                || payment.getStatus() == PaymentStatus.ABORTED
                || payment.getStatus() == PaymentStatus.MANUAL_RECOVERY_REQUIRED) {
            return;
        }

        boolean isFullTossCanceled = "CANCELED".equals(latestTossPayment.status());

        if (payment.getStatus() == PaymentStatus.READY
                || payment.getStatus() == PaymentStatus.IN_PROGRESS
                || payment.getStatus() == PaymentStatus.FAILED) {
            if (latestTossPayment.approvedAt() == null) {
                payment.abort(latestTossPayment.paymentKey());
                return;
            }
            if (payment.isReservationPayment() && payment.getReservation().getStatus() == ReservationStatus.HOLD) {
                payment.getReservation().cancelWithoutPayment();
            }
            payment.complete(
                    latestTossPayment.paymentKey(),
                    latestTossPayment.method(),
                    parseTossDateTime(latestTossPayment.approvedAt()));
        }

        List<TossPaymentResponse.TossCancel> cancels =
                latestTossPayment.cancels() != null ? latestTossPayment.cancels() : List.of();
        List<TossPaymentResponse.TossCancel> newCancels = cancels.stream()
                .filter(c -> c != null && c.cancelAmount() != null && c.cancelAmount() > 0)
                .filter(c -> c.transactionKey() == null
                        || c.transactionKey().isBlank()
                        || !paymentCancelHistoryRepository.existsByTransactionKey(c.transactionKey()))
                .toList();

        if (latestTossPayment.balanceAmount() != null
                && payment.getBalanceAmount().equals(latestTossPayment.balanceAmount())
                && isFullTossCanceled == (payment.getStatus() == PaymentStatus.CANCELED)) {
            return;
        }

        long remainingToSync = latestTossPayment.balanceAmount() != null
                ? Math.max(0L, payment.getBalanceAmount() - latestTossPayment.balanceAmount())
                : payment.getBalanceAmount();

        if (remainingToSync <= 0 && !isFullTossCanceled) {
            return;
        }

        if (!newCancels.isEmpty() && remainingToSync > 0) {
            for (TossPaymentResponse.TossCancel cancel : newCancels) {
                if (remainingToSync <= 0 || payment.getBalanceAmount() <= 0) {
                    break;
                }
                long applyAmount =
                        Math.min(cancel.cancelAmount(), Math.min(remainingToSync, payment.getBalanceAmount()));
                if (applyAmount <= 0) {
                    continue;
                }

                boolean closingFullBalance = isFullTossCanceled && applyAmount == payment.getBalanceAmount();
                String itemName = closingFullBalance ? "PG 콘솔 전체 취소" : "PG 콘솔 부분 취소";
                String reason =
                        (cancel.cancelReason() != null && !cancel.cancelReason().isBlank())
                                ? cancel.cancelReason()
                                : itemName;

                if (closingFullBalance) {
                    releaseRemainingTargetResourcesOnFullCancel(payment);
                } else {
                    log.warn(
                            "PG 콘솔 직접 부분 취소 웹훅 반영 (좌석/품목 매핑 수동 확인 필요): orderId={}, paymentKey={}, cancelAmount={}, transactionKey={}",
                            payment.getOrderId(),
                            payment.getPaymentKey(),
                            applyAmount,
                            cancel.transactionKey());
                }

                payment.cancel(
                        null,
                        cancel.transactionKey(),
                        null,
                        null,
                        itemName,
                        applyAmount,
                        reason,
                        parseTossDateTime(cancel.canceledAt()));
                remainingToSync -= applyAmount;
            }
        } else if (isFullTossCanceled && payment.getBalanceAmount() > 0) {
            releaseRemainingTargetResourcesOnFullCancel(payment);
            long remainingBalance = payment.getBalanceAmount();
            payment.cancel(null, null, null, null, "PG 콘솔 전체 취소", remainingBalance, "PG 콘솔 전체 취소", LocalDateTime.now());
        }

        paymentRepository.saveAndFlush(payment);
    }

    private void releaseRemainingTargetResourcesOnFullCancel(Payment payment) {
        if (payment.isReservationPayment()) {
            Reservation reservation = payment.getReservation();
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                reservation.cancelByPayment(null);
            } else if (reservation.getStatus() == ReservationStatus.HOLD) {
                reservation.cancelWithoutPayment();
            }
        } else {
            restoreRemainingOrderItemsAndStocks(payment.getOrder());
        }
    }

    private Payment findPaymentLockedInOrderByOrderId(String orderId) {
        Payment lookup = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        lockTarget(lookup);
        return paymentRepository
                .findByOrderIdWithLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private Payment findPaymentLockedInOrderByPaymentKey(String paymentKey) {
        Payment lookup = paymentRepository
                .findByPaymentKey(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        lockTarget(lookup);
        return paymentRepository
                .findByPaymentKeyWithLock(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private Payment findLookupByOrderIdOrPaymentKey(String orderId, String paymentKey) {
        if (orderId != null && !orderId.isBlank()) {
            Optional<Payment> byOrderId = paymentRepository.findByOrderId(orderId);
            if (byOrderId.isPresent()) {
                return byOrderId.get();
            }
        }
        if (paymentKey != null && !paymentKey.isBlank()) {
            return paymentRepository.findByPaymentKey(paymentKey).orElse(null);
        }
        return null;
    }

    private void lockTarget(Payment lookup) {
        if (lookup.isReservationPayment()) {
            reservationRepository
                    .findByIdWithLock(lookup.getReservation().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        } else {
            orderRepository
                    .findByIdWithLock(lookup.getOrder().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        }
    }

    private void restoreSingleMenuStock(Order order, OrderItem orderItem, Long quantity) {
        if (order == null
                || order.getStore() == null
                || order.getStore().getId() == null
                || orderItem == null
                || orderItem.getMenu() == null
                || orderItem.getMenu().getId() == null) {
            return;
        }
        menuStockRepository
                .findByStoreIdAndMenuId(
                        order.getStore().getId(), orderItem.getMenu().getId())
                .ifPresent(menuStock -> menuStock.increaseStock(quantity));
    }

    private void restoreRemainingOrderItemsAndStocks(Order order) {
        if (order == null) {
            return;
        }
        List<OrderItem> remainingItems = order.getOrderItems().stream()
                .filter(oi -> oi.getCancelableQuantity() != null && oi.getCancelableQuantity() > 0)
                .sorted(Comparator.comparing(oi -> oi.getMenu().getId()))
                .toList();
        if (remainingItems.isEmpty()) {
            return;
        }

        Long storeId = order.getStore() != null ? order.getStore().getId() : null;
        if (storeId == null) {
            for (OrderItem oi : remainingItems) {
                oi.cancelQuantity(oi.getCancelableQuantity());
            }
            return;
        }

        List<Long> menuIds = remainingItems.stream()
                .map(oi -> oi.getMenu().getId())
                .distinct()
                .sorted()
                .toList();
        Map<Long, MenuStock> stockMap =
                menuStockRepository.findAllByStoreIdAndMenuIdInOrderByMenuIdAsc(storeId, menuIds).stream()
                        .collect(Collectors.toMap(ms -> ms.getMenu().getId(), ms -> ms));

        for (OrderItem oi : remainingItems) {
            Long qty = oi.getCancelableQuantity();
            oi.cancelQuantity(qty);
            MenuStock stock = stockMap.get(oi.getMenu().getId());
            if (stock != null) {
                stock.increaseStock(qty);
            }
        }
    }

    private String createOrderName(Order order) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return "CGV 스토어 상품 주문";
        }
        String firstItemName = order.getOrderItems().get(0).getMenu().getName();
        int extraCount = order.getOrderItems().size() - 1;
        return extraCount > 0 ? firstItemName + " 외 " + extraCount + "건" : firstItemName;
    }

    private String createReservationName(Reservation reservation) {
        int seatCount = reservation.getSeatReservations().size();
        if (reservation.getScreening() != null && reservation.getScreening().getMovie() != null) {
            return reservation.getScreening().getMovie().getTitle() + " (" + seatCount + "매)";
        }
        return "CGV 영화 예매 (" + seatCount + "매)";
    }

    private LocalDateTime parseTossDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(dateTimeStr).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(dateTimeStr);
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.now();
            }
        }
    }

    private String normalizeBlankToNull(String value) {
        return (value != null && !value.isBlank()) ? value : null;
    }

    public record ConfirmStartResult(
            boolean alreadyCompleted,
            boolean recoveryRequired,
            PaymentResponse response,
            String paymentKey,
            String orderId,
            Long amount) {

        public static ConfirmStartResult completed(PaymentResponse response) {
            return new ConfirmStartResult(
                    true, false, response, response.paymentKey(), response.orderId(), response.totalAmount());
        }

        public static ConfirmStartResult recover(String paymentKey, String orderId, Long amount) {
            return new ConfirmStartResult(false, true, null, paymentKey, orderId, amount);
        }

        public static ConfirmStartResult readyToConfirm(String paymentKey, String orderId, Long amount) {
            return new ConfirmStartResult(false, false, null, paymentKey, orderId, amount);
        }
    }

    public record CompleteResult(boolean alreadyAborted, PaymentResponse response) {}

    public record CancelPlan(
            boolean alreadyCanceled,
            PaymentResponse response,
            String orderId,
            Long cancelAmount,
            String effectiveIdempotencyKey,
            String normalizedCancelRequestId,
            Long orderItemId,
            Long cancelQuantity,
            String itemName,
            List<Long> sortedSeatNumbers,
            boolean cancelAllOrderItems) {

        public static CancelPlan alreadyCanceled(PaymentResponse response) {
            return new CancelPlan(true, response, null, null, null, null, null, null, null, null, false);
        }

        public static CancelPlan proceed(
                String orderId,
                Long cancelAmount,
                String effectiveIdempotencyKey,
                String normalizedCancelRequestId,
                Long orderItemId,
                Long cancelQuantity,
                String itemName,
                List<Long> sortedSeatNumbers,
                boolean cancelAllOrderItems) {
            return new CancelPlan(
                    false,
                    null,
                    orderId,
                    cancelAmount,
                    effectiveIdempotencyKey,
                    normalizedCancelRequestId,
                    orderItemId,
                    cancelQuantity,
                    itemName,
                    sortedSeatNumbers,
                    cancelAllOrderItems);
        }
    }
}
