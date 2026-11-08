package com.ceos.cgv.domain.payment.service.transaction;

import com.ceos.cgv.domain.payment.config.PaymentPricingProperties;
import com.ceos.cgv.domain.payment.config.PaymentRecoveryProperties;
import com.ceos.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos.cgv.domain.payment.dto.request.PaymentOrderCreateRequest;
import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.entity.Payment;
import com.ceos.cgv.domain.payment.entity.PaymentOperation;
import com.ceos.cgv.domain.payment.entity.PaymentOrder;
import com.ceos.cgv.domain.payment.entity.PaymentOrderSeat;
import com.ceos.cgv.domain.payment.enums.PaymentOperationType;
import com.ceos.cgv.domain.payment.enums.PaymentStatus;
import com.ceos.cgv.domain.payment.gateway.GatewayCancellation;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.domain.payment.gateway.GatewayPaymentStatus;
import com.ceos.cgv.domain.payment.repository.PaymentOperationRepository;
import com.ceos.cgv.domain.payment.repository.PaymentOrderRepository;
import com.ceos.cgv.domain.payment.repository.PaymentRepository;
import com.ceos.cgv.domain.payment.service.PaymentJson;
import com.ceos.cgv.domain.payment.service.PaymentPayload;
import com.ceos.cgv.domain.payment.service.PaymentQueryService;
import com.ceos.cgv.domain.payment.service.PaymentResponses;
import com.ceos.cgv.domain.payment.service.result.PaymentOrderCreationResult;
import com.ceos.cgv.domain.payment.service.result.PaymentWork;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.service.payment.PaymentReservationSeat;
import com.ceos.cgv.domain.reservation.service.payment.PaymentReservationSnapshot;
import com.ceos.cgv.domain.reservation.service.payment.ReservationPaymentService;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PaymentTransactionService {
    private static final int MAX_ORDER_NAME_LENGTH = 100;

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final ReservationPaymentService reservationPaymentService;
    private final PaymentPricingProperties pricingProperties;
    private final PaymentRecoveryProperties recoveryProperties;
    private final PaymentResponses paymentResponses;
    private final PaymentJson paymentJson;
    private final PaymentPayload paymentPayload;
    private final Clock paymentClock;
    private final EntityManager entityManager;

    public PaymentOrderCreationResult createOrder(Long userId, PaymentOrderCreateRequest request) {
        if (request == null || request.reservationId() == null || request.reservationId() <= 0)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        PaymentReservationSnapshot snapshot =
                reservationPaymentService.lockOwned(request.reservationId(), userId);
        Optional<PaymentOrder> existing =
                paymentOrderRepository.findByReservationId(request.reservationId());
        if (existing.isPresent())
            return new PaymentOrderCreationResult(
                    false,
                    paymentResponses.order(
                            paymentRepository
                                    .findByOrder_Id(existing.get().getId())
                                    .orElseThrow()));
        if (snapshot.status() != ReservationStatus.HELD)
            throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
        if (snapshot.expiresAt() == null || !paymentClock.instant().isBefore(snapshot.expiresAt()))
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        List<PaymentOrderSeat> seats =
                snapshot.seats().stream()
                        .filter(seat -> seat.canceledAt() == null)
                        .map(
                                seat ->
                                        PaymentOrderSeat.builder()
                                                .reservedSeatId(seat.reservedSeatId())
                                                .seatRow(seat.seatRow())
                                                .seatNumber(seat.seatNumber())
                                                .build())
                        .toList();
        PaymentOrder order =
                paymentOrderRepository.save(
                        PaymentOrder.builder()
                                .orderId(UUID.randomUUID().toString())
                                .reservationId(snapshot.reservationId())
                                .userId(userId)
                                .orderName(createOrderName(snapshot.movieTitle(), seats.size()))
                                .seatPrice(pricingProperties.seatPrice())
                                .expiresAt(snapshot.expiresAt())
                                .seats(seats)
                                .build());
        return new PaymentOrderCreationResult(
                true,
                paymentResponses.order(
                        paymentRepository.saveAndFlush(Payment.builder().order(order).build())));
    }

    public PaymentWork claimApproval(Long userId, UUID key, PaymentConfirmRequest request) {
        String hash = paymentPayload.confirm(request);
        validateKey(key);
        Optional<PaymentOperation> prior =
                paymentOperationRepository.findByUserIdAndTypeAndRequestKey(
                        userId, PaymentOperationType.CONFIRM, key.toString());
        if (prior.isPresent()) return replay(lockOperation(prior.get().getId(), userId), hash);
        Long id =
                paymentRepository
                        .findIdByOrderId(request.orderId())
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
        Payment payment = lockPayment(id, userId);
        Optional<PaymentOperation> replay =
                paymentOperationRepository.findByUserIdAndTypeAndRequestKey(
                        userId, PaymentOperationType.CONFIRM, key.toString());
        if (replay.isPresent()) return replay(replay.get(), hash);
        if (payment.getOrder().getTotalAmount() != request.amount())
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        if (payment.getPaymentKey() != null
                && !payment.getPaymentKey().equals(request.paymentKey()))
            throw new BusinessException(ErrorCode.PAYMENT_REQUEST_CONFLICT);
        if (payment.getActiveOperationId() != null)
            return work(
                    paymentOperationRepository
                            .findById(payment.getActiveOperationId())
                            .orElseThrow(),
                    false);
        if (payment.getStatus() == PaymentStatus.DONE) {
            PaymentOperation operation =
                    newOperation(
                            userId,
                            payment,
                            PaymentOperationType.CONFIRM,
                            key,
                            hash,
                            payment.getOrder().getTotalAmount(),
                            payment.getOrder().getTotalAmount(),
                            List.of(),
                            "");
            operation.succeed(paymentJson.write(paymentResponses.payment(payment)));
            return work(operation, false);
        }
        if (payment.getStatus() != PaymentStatus.READY)
            throw new BusinessException(ErrorCode.PAYMENT_STATE_CONFLICT);
        if (paymentRepository.existsByPaymentKey(request.paymentKey()))
            throw new BusinessException(ErrorCode.PAYMENT_KEY_CONFLICT);
        reservationPaymentService.startPayment(payment.getOrder().getReservationId(), userId);
        PaymentOperation operation =
                newOperation(
                        userId,
                        payment,
                        PaymentOperationType.CONFIRM,
                        key,
                        hash,
                        request.amount(),
                        request.amount(),
                        List.of(),
                        "");
        payment.claimApproval(request.paymentKey(), operation.getId());
        paymentRepository.flush();
        return work(operation, true);
    }

    public PaymentWork claimCancellation(
            Long userId, Long paymentId, UUID key, PaymentCancelRequest request) {
        String hash = paymentPayload.cancel(paymentId, request);
        validateKey(key);
        Optional<PaymentOperation> prior =
                paymentOperationRepository.findByUserIdAndTypeAndRequestKey(
                        userId, PaymentOperationType.CANCEL, key.toString());
        if (prior.isPresent()) return replay(lockOperation(prior.get().getId(), userId), hash);
        Payment payment = lockPayment(paymentId, userId);
        Optional<PaymentOperation> existing =
                paymentOperationRepository.findByUserIdAndTypeAndRequestKey(
                        userId, PaymentOperationType.CANCEL, key.toString());
        if (existing.isPresent()) return replay(existing.get(), hash);
        List<Long> active =
                payment.getOrder().getSeats().stream()
                        .filter(seat -> seat.getCanceledAt() == null)
                        .map(PaymentOrderSeat::getReservedSeatId)
                        .sorted()
                        .toList();
        // 남은 좌석이 없는 전체 취소는 잘못된 좌석 요청이 아니라 이미 끝난 취소다.
        if (request.reservedSeatIds() == null && active.isEmpty())
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED);
        List<Long> requested =
                request.reservedSeatIds() == null
                        ? active
                        : request.reservedSeatIds().stream().sorted().toList();
        if (requested.isEmpty() || !active.containsAll(requested)) {
            throw new BusinessException(ErrorCode.INVALID_SEAT);
        }
        if (payment.getActiveOperationId() != null) {
            return work(
                    paymentOperationRepository
                            .findById(payment.getActiveOperationId())
                            .orElseThrow(),
                    false);
        }
        if (payment.getStatus() != PaymentStatus.DONE
                && payment.getStatus() != PaymentStatus.PARTIALLY_CANCELED)
            throw new BusinessException(ErrorCode.PAYMENT_STATE_CONFLICT);
        PaymentReservationSnapshot reservation =
                reservationPaymentService.lockOwned(payment.getOrder().getReservationId(), userId);
        if (reservation.status() != ReservationStatus.RESERVED)
            throw new BusinessException(ErrorCode.PAYMENT_STATE_CONFLICT);
        List<Long> reservationActive =
                reservation.seats().stream()
                        .filter(seat -> seat.canceledAt() == null)
                        .map(PaymentReservationSeat::reservedSeatId)
                        .toList();
        if (requested.isEmpty()
                || !active.containsAll(requested)
                || !reservationActive.containsAll(requested))
            throw new BusinessException(ErrorCode.INVALID_SEAT);
        long amount = Math.multiplyExact(payment.getOrder().getSeatPrice(), requested.size());
        if (amount > payment.getBalanceAmount())
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        PaymentOperation operation =
                newOperation(
                        userId,
                        payment,
                        PaymentOperationType.CANCEL,
                        key,
                        hash,
                        amount,
                        payment.getBalanceAmount(),
                        requested,
                        request.reason());
        payment.claimCancellation(operation.getId());
        paymentRepository.flush();
        return work(operation, true);
    }

    public PaymentWork recovery(Long userId, Long operationId) {
        PaymentOperation operation = lockOperation(operationId, userId);
        return work(operation, !operation.isTerminal());
    }

    public PaymentOperationResponse recoverApproval(
            Long userId, Long operationId, GatewayPayment result) {
        PaymentOperation operation = lockOperation(operationId, userId);
        if (operation.isTerminal() || operation.isCompensating())
            return paymentResponses.operation(operation);
        Payment payment = operation.getPayment();
        if (matches(payment, result)
                && (result.status() == GatewayPaymentStatus.ABORTED
                        || result.status() == GatewayPaymentStatus.EXPIRED)
                && result.cancellations().isEmpty()) {
            reservationPaymentService.releaseFailedPayment(
                    payment.getOrder().getReservationId(), userId);
            payment.failedApproval();
            operation.fail(
                    "GATEWAY_" + result.status().name(),
                    paymentJson.write(paymentResponses.payment(payment)));
            return paymentResponses.operation(operation);
        }
        if (matches(payment, result)
                && (result.status() == GatewayPaymentStatus.CANCELED
                        || result.status() == GatewayPaymentStatus.PARTIAL_CANCELED)
                && validCancellation(operation, result)) {
            operation.startCompensation("EXTERNALLY_CANCELED");
            payment.compensate();
            reservationPaymentService.releaseFailedPayment(
                    payment.getOrder().getReservationId(), userId);
            payment.compensated(result, paymentJson.write(result.cancellations()));
            operation.fail(
                    "APPROVAL_EXTERNALLY_CANCELED",
                    paymentJson.write(paymentResponses.payment(payment)));
            return paymentResponses.operation(operation);
        }
        return applyApproval(operation, result);
    }

    public PaymentOperationResponse approved(Long userId, Long operationId, GatewayPayment result) {
        PaymentOperation operation = lockOperation(operationId, userId);
        if (operation.isTerminal() || operation.isCompensating())
            return paymentResponses.operation(operation);
        return applyApproval(operation, result);
    }

    private PaymentOperationResponse applyApproval(
            PaymentOperation operation, GatewayPayment result) {
        Payment payment = operation.getPayment();
        if (operation.getType() != PaymentOperationType.CONFIRM
                || !matches(payment, result)
                || result.status() != GatewayPaymentStatus.DONE
                || result.balanceAmount() != payment.getOrder().getTotalAmount()
                || !result.cancellations().isEmpty()
                || result.approvedAt() == null) {
            operation.unknown("INVALID_APPROVAL_RESULT");
            return paymentResponses.operation(operation);
        }
        reservationPaymentService.confirmPayment(
                payment.getOrder().getReservationId(), operation.getUserId());
        payment.approve(result, paymentJson.write(result.cancellations()));
        operation.succeed(paymentJson.write(paymentResponses.payment(payment)));
        return paymentResponses.operation(operation);
    }

    public PaymentOperationResponse canceled(Long userId, Long operationId, GatewayPayment result) {
        PaymentOperation operation = lockOperation(operationId, userId);
        if (operation.isTerminal()) return paymentResponses.operation(operation);
        Payment payment = operation.getPayment();
        if (!validCancellation(operation, result)) {
            operation.unknown("INVALID_CANCELLATION_RESULT");
            return paymentResponses.operation(operation);
        }
        if (operation.isCompensating()) {
            reservationPaymentService.releaseFailedPayment(
                    payment.getOrder().getReservationId(), userId);
            payment.compensated(result, paymentJson.write(result.cancellations()));
            operation.fail(
                    "APPROVAL_COMPENSATED", paymentJson.write(paymentResponses.payment(payment)));
        } else {
            List<Long> ids = paymentJson.seatIds(operation.getSeatIdsJson());
            // 예매 좌석과 주문 좌석의 취소 시각이 같도록 한 번만 읽는다.
            Instant canceledAt = paymentClock.instant();
            reservationPaymentService.cancelPaidSeats(
                    payment.getOrder().getReservationId(), userId, ids, canceledAt);
            payment.getOrder().cancelSeats(ids, canceledAt);
            payment.canceled(result, paymentJson.write(result.cancellations()));
            operation.succeed(paymentJson.write(paymentResponses.payment(payment)));
        }
        return paymentResponses.operation(operation);
    }

    public PaymentOperationResponse rejected(Long userId, Long operationId, String code) {
        PaymentOperation operation = lockOperation(operationId, userId);
        if (operation.isTerminal()) return paymentResponses.operation(operation);
        if (operation.isCompensating()) {
            operation.unknown(code);
            return paymentResponses.operation(operation);
        }
        Payment payment = operation.getPayment();
        if (operation.getType() == PaymentOperationType.CONFIRM) {
            reservationPaymentService.releaseFailedPayment(
                    payment.getOrder().getReservationId(), userId);
            payment.failedApproval();
        } else payment.failedCancellation();
        operation.fail(code, paymentJson.write(paymentResponses.payment(payment)));
        return paymentResponses.operation(operation);
    }

    public PaymentOperationResponse unknown(Long userId, Long operationId, String code) {
        PaymentOperation operation = lockOperation(operationId, userId);
        operation.unknown(code);
        return paymentResponses.operation(operation);
    }

    public boolean compensationCanResume(Long userId, Long operationId, GatewayPayment result) {
        PaymentOperation operation = lockOperation(operationId, userId);
        return !operation.isTerminal()
                && operation.isCompensating()
                && matches(operation.getPayment(), result)
                && result.status() == GatewayPaymentStatus.DONE
                && result.balanceAmount() == operation.getPayment().getOrder().getTotalAmount()
                && result.cancellations().isEmpty();
    }

    // PG가 취소를 처리하지 않은 상태가 확인된 UNKNOWN 취소만 같은 멱등키로 다시 보낸다.
    public boolean cancellationCanResume(Long userId, Long operationId, GatewayPayment result) {
        PaymentOperation operation = lockOperation(operationId, userId);
        Payment payment = operation.getPayment();
        if (operation.isTerminal()
                || operation.isCompensating()
                || operation.getType() != PaymentOperationType.CANCEL
                || !matches(payment, result)
                || result.balanceAmount() != operation.getPreviousBalance()
                || (result.status() != GatewayPaymentStatus.DONE
                        && result.status() != GatewayPaymentStatus.PARTIAL_CANCELED)) return false;
        List<GatewayCancellation> stored =
                paymentJson.cancellations(payment.getCancellationsJson());
        return stored.size() == result.cancellations().size()
                && new HashSet<>(stored).equals(new HashSet<>(result.cancellations()));
    }

    public PaymentOperationResponse approvalNotFound(
            Long userId, Long operationId, String code) {
        PaymentOperation operation = lockOperation(operationId, userId);
        if (operation.isTerminal()
                || operation.isCompensating()
                || operation.getType() != PaymentOperationType.CONFIRM)
            return paymentResponses.operation(operation);
        Payment payment = operation.getPayment();
        // 선점 기한과 유예 시간이 지나기 전의 NOT_FOUND는 아직 승인 결과를 단정하지 않는다.
        Instant giveUpAt =
                payment.getOrder().getExpiresAt().plus(recoveryProperties.approvalNotFoundGrace());
        if (paymentClock.instant().isBefore(giveUpAt)) {
            operation.unknown(code);
            return paymentResponses.operation(operation);
        }
        reservationPaymentService.releaseFailedPayment(
                payment.getOrder().getReservationId(), userId);
        payment.failedApproval();
        operation.fail("GATEWAY_NOT_FOUND", paymentJson.write(paymentResponses.payment(payment)));
        return paymentResponses.operation(operation);
    }

    public PaymentWork startCompensation(Long userId, Long operationId, String code) {
        PaymentOperation operation = lockOperation(operationId, userId);
        if (operation.isTerminal()) return work(operation, false);
        operation.startCompensation(code);
        operation.getPayment().compensate();
        return work(operation, true);
    }

    private static String createOrderName(String movieTitle, int seatCount) {
        String suffix = " " + seatCount + "석";
        int titleEnd = Math.min(movieTitle.length(), MAX_ORDER_NAME_LENGTH - suffix.length());
        // Java와 JavaScript의 UTF-16 길이 한도를 지키면서 서로게이트 쌍을 보존한다.
        if (titleEnd > 0
                && titleEnd < movieTitle.length()
                && Character.isHighSurrogate(movieTitle.charAt(titleEnd - 1))
                && Character.isLowSurrogate(movieTitle.charAt(titleEnd))) {
            titleEnd--;
        }
        return movieTitle.substring(0, titleEnd) + suffix;
    }

    private boolean matches(Payment payment, GatewayPayment result) {
        return result != null
                && Objects.equals(payment.getPaymentKey(), result.paymentKey())
                && Objects.equals(payment.getOrder().getOrderId(), result.orderId())
                && result.totalAmount() == payment.getOrder().getTotalAmount();
    }

    private boolean validCancellation(PaymentOperation operation, GatewayPayment result) {
        Payment payment = operation.getPayment();
        long expected =
                operation.isCompensating()
                        ? 0
                        : operation.getPreviousBalance() - operation.getRequestAmount();
        // 누적 부분취소는 잔액이 0이어도 PG 상태가 PARTIAL_CANCELED로 유지될 수 있다.
        if (!matches(payment, result)
                || result.balanceAmount() != expected
                || (result.status() != GatewayPaymentStatus.PARTIAL_CANCELED
                        && (expected != 0 || result.status() != GatewayPaymentStatus.CANCELED)))
            return false;
        List<GatewayCancellation> existing =
                paymentJson.cancellations(payment.getCancellationsJson());
        if (!result.cancellations().containsAll(existing)) return false;
        Set<String> keys = new HashSet<>();
        long sum = 0;
        try {
            for (GatewayCancellation cancellation : result.cancellations()) {
                if (cancellation.transactionKey() == null
                        || !keys.add(cancellation.transactionKey())
                        || cancellation.amount() <= 0
                        || !"DONE".equals(cancellation.status())
                        || cancellation.canceledAt() == null) return false;
                sum = Math.addExact(sum, cancellation.amount());
            }
        } catch (ArithmeticException exception) {
            return false;
        }
        return sum == result.totalAmount() - expected;
    }

    private Payment lockPayment(Long paymentId, Long userId) {
        Payment payment =
                paymentRepository
                        .lockById(paymentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        PaymentQueryService.owned(payment, userId);
        return payment;
    }

    private PaymentOperation lockOperation(Long operationId, Long userId) {
        PaymentOperation operation =
                paymentOperationRepository
                        .findById(operationId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.PAYMENT_OPERATION_NOT_FOUND));
        if (!operation.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        lockPayment(operation.getPayment().getId(), userId);
        entityManager.refresh(operation);
        return operation;
    }

    private PaymentWork replay(PaymentOperation operation, String hash) {
        if (!operation.getPayloadHash().equals(hash))
            throw new BusinessException(ErrorCode.PAYMENT_REQUEST_CONFLICT);
        return work(operation, false);
    }

    private PaymentOperation newOperation(
            Long userId,
            Payment payment,
            PaymentOperationType type,
            UUID key,
            String hash,
            long amount,
            long previous,
            List<Long> seats,
            String reason) {
        return paymentOperationRepository.saveAndFlush(
                PaymentOperation.builder()
                        .userId(userId)
                        .payment(payment)
                        .type(type)
                        .requestKey(key.toString())
                        .payloadHash(hash)
                        .requestAmount(amount)
                        .previousBalance(previous)
                        .seatIdsJson(paymentJson.write(seats))
                        .reason(reason)
                        .build());
    }

    private void validateKey(UUID key) {
        if (key == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    private PaymentWork work(PaymentOperation operation, boolean invoke) {
        Payment payment = operation.getPayment();
        boolean compensation = operation.isCompensating();
        return new PaymentWork(
                operation.getId(),
                operation.getType(),
                payment.getPaymentKey(),
                payment.getOrder().getOrderId(),
                compensation ? payment.getOrder().getTotalAmount() : operation.getRequestAmount(),
                compensation ? payment.getOrder().getTotalAmount() : operation.getPreviousBalance(),
                compensation ? "예약 확정 실패 보상 취소" : operation.getReason(),
                compensation ? operation.getCompensationKey() : operation.getGatewayKey(),
                compensation,
                invoke,
                paymentResponses.operation(operation));
    }
}
