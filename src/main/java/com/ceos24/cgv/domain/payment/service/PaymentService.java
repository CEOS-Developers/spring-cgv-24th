package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.order.entity.Order;
import com.ceos24.cgv.domain.order.entity.OrderItem;
import com.ceos24.cgv.domain.order.repository.OrderRepository;
import com.ceos24.cgv.domain.payment.dto.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareResponse;
import com.ceos24.cgv.domain.payment.dto.PaymentResponse;
import com.ceos24.cgv.domain.payment.dto.TossCancelRequest;
import com.ceos24.cgv.domain.payment.dto.TossConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancelHistory;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import com.ceos24.cgv.domain.payment.repository.PaymentCancelHistoryRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.store.repository.MenuStockRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentCancelHistoryRepository paymentCancelHistoryRepository;
    private final OrderRepository orderRepository;
    private final MenuStockRepository menuStockRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentRecoveryService paymentRecoveryService;

    @Transactional
    public PaymentPrepareResponse preparePayment(PaymentPrepareRequest request) {
        Order order = orderRepository
                .findById(request.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Optional<Payment> existingPayment = paymentRepository.findByOrder(order);
        if (existingPayment.isPresent()) {
            Payment p = existingPayment.get();
            if (p.getStatus() == PaymentStatus.DONE) {
                throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED, "이미 결제가 완료된 주문입니다.");
            }
            if (p.getStatus() == PaymentStatus.READY) {
                return new PaymentPrepareResponse(
                        p.getOrderId(), p.getTotalAmount(), createOrderName(order), p.getId());
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

    @Transactional
    public PaymentResponse confirmPayment(PaymentConfirmRequest request) {
        // 1. 결제 데이터 조회
        Payment payment = paymentRepository
                .findByOrderId(request.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 2. 이미 승인 완료(DONE)된 경우 멱등 반환
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
            return PaymentResponse.from(payment);
        }

        // 3. 결제 상태 검증 (READY 상태에서만 승인 가능)
        if (payment.getStatus() != PaymentStatus.READY) {
            log.warn("결제 승인 거절: 승인 가능한 상태가 아님. orderId={}, status={}", request.orderId(), payment.getStatus());
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        // 4. 결제 금액 검증
        if (!payment.getTotalAmount().equals(request.amount())) {
            log.error(
                    "결제 금액 불일치: DB 금액={}원, 요청 금액={}원 (orderId={})",
                    payment.getTotalAmount(),
                    request.amount(),
                    request.orderId());
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 5. 토스페이먼츠 승인 API 호출 (orderId를 Idempotency-Key로 전달)
        TossPaymentResponse tossResponse;
        try {
            tossResponse = tossPaymentsClient.confirm(
                    new TossConfirmRequest(request.paymentKey(), request.orderId(), request.amount()),
                    request.orderId());
        } catch (TossPaymentException e) {
            log.error(
                    "토스 결제 승인 API 호출 실패: orderId={}, code={}, message={}",
                    request.orderId(),
                    e.getTossCode(),
                    e.getMessage());
            if (e.isClientError()) {
                paymentRecoveryService.recordAbortByOrderId(request.orderId());
            } else {
                paymentRecoveryService.recordFailByOrderId(request.orderId());
            }
            throw e;
        } catch (Exception e) {
            log.error("토스 결제 승인 API 호출 중 예외 발생: orderId={}, error={}", request.orderId(), e.getMessage());
            paymentRecoveryService.recordFailByOrderId(request.orderId());
            throw e;
        }

        // 6. 응답 필수 필드 및 금액 검증
        try {
            tossResponse.validateIntegrity(request.orderId(), request.amount());
        } catch (BusinessException integrityEx) {
            log.error("토스 승인 응답 검증 실패: orderId={}, error={}", request.orderId(), integrityEx.getMessage());
            executeCompensatingCancel(
                    request.orderId(),
                    tossResponse.paymentKey() != null ? tossResponse.paymentKey() : request.paymentKey(),
                    request.amount(),
                    "비정상 승인 응답 수신으로 인한 자동 보상 취소",
                    integrityEx);
        }

        // 7. 승인 완료 상태 반영 (실패 시 자동 보상 취소)
        try {
            LocalDateTime approvedAt = tossResponse.approvedAt() != null
                    ? OffsetDateTime.parse(tossResponse.approvedAt()).toLocalDateTime()
                    : LocalDateTime.now();
            payment.complete(tossResponse.paymentKey(), tossResponse.method(), approvedAt);
            paymentRepository.saveAndFlush(payment);
        } catch (Exception dbException) {
            log.error(
                    "결제 승인 후 DB 반영 중 오류 발생으로 보상 취소 진행: orderId={}, error={}",
                    request.orderId(),
                    dbException.getMessage(),
                    dbException);
            executeCompensatingCancel(
                    request.orderId(),
                    tossResponse.paymentKey(),
                    request.amount(),
                    "시스템 오류로 인한 자동 보상 취소",
                    new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR, "주문 처리 중 오류가 발생하여 결제가 자동으로 취소되었습니다."));
        }

        return PaymentResponse.from(payment);
    }

    private void executeCompensatingCancel(
            String orderId, String paymentKey, Long amount, String reason, RuntimeException exceptionToThrowOnSuccess) {
        log.warn("보상 취소 실행: orderId={}, paymentKey={}, amount={}, reason={}", orderId, paymentKey, amount, reason);
        boolean cancelSucceeded = false;
        try {
            tossPaymentsClient.cancel(paymentKey, new TossCancelRequest(reason, amount), "COMPENSATION_" + orderId);
            log.info("보상 취소 완료 (orderId={})", orderId);
            cancelSucceeded = true;
            paymentRecoveryService.recordCompensatedAbort(orderId, paymentKey);
        } catch (Exception cancelEx) {
            handleCompensationFailure(orderId, paymentKey, cancelEx);
        }

        if (cancelSucceeded) {
            throw exceptionToThrowOnSuccess;
        }
    }

    private void handleCompensationFailure(String orderId, String paymentKey, Exception cancelEx) {
        log.error(
                "결제 승인 후 보상 취소 API 호출 실패 (orderId={}, paymentKey={}). MANUAL_RECOVERY_REQUIRED 상태로 기록합니다.",
                orderId,
                paymentKey,
                cancelEx);
        paymentRecoveryService.recordManualRecoveryRequired(orderId, paymentKey);
        throw new BusinessException(
                ErrorCode.PAYMENT_COMPENSATION_FAILED, "주문 처리 중 시스템 오류가 발생하였으며 자동 결제 취소에도 실패했습니다. 관리자 확인이 필요합니다.");
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentKey) {
        Payment payment = paymentRepository
                .findByPaymentKey(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse cancelPayment(String paymentKey, PaymentCancelRequest request) {
        // 1. paymentKey로 결제 조회
        Payment payment = paymentRepository
                .findByPaymentKey(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 2. 취소 멱등성 검증 (cancelRequestId가 주어졌을 때 이미 처리된 취소인지 확인)
        if (request.cancelRequestId() != null && !request.cancelRequestId().isBlank()) {
            Optional<PaymentCancelHistory> existingCancel =
                    paymentCancelHistoryRepository.findByCancelRequestId(request.cancelRequestId());
            if (existingCancel.isPresent()) {
                log.info("결제 취소 멱등 응답: 이미 처리된 취소 요청 (cancelRequestId={})", request.cancelRequestId());
                return PaymentResponse.from(payment);
            }
        }

        // 3. 이미 전액 취소된 결제 재취소 차단
        if (payment.getStatus() == PaymentStatus.CANCELED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CANCELED);
        }

        OrderItem targetItem = null;
        Long cancelQty = null;
        String itemName = "전체 취소";
        Long cancelAmount;

        // 4. 품목 기반 부분 취소 vs 전체/금액 기반 취소
        if (request.orderItemId() != null) {
            targetItem = payment.getOrder().getOrderItems().stream()
                    .filter(oi -> oi.getId().equals(request.orderItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND));

            cancelQty = request.cancelQuantity() != null ? request.cancelQuantity() : 1L;
            itemName = targetItem.getMenu().getName();

            cancelAmount = targetItem.cancelQuantity(cancelQty);
            restoreMenuStock(payment.getOrder(), targetItem, cancelQty);
        } else {
            cancelAmount = request.cancelAmount() != null ? request.cancelAmount() : payment.getBalanceAmount();
            if (cancelAmount.equals(payment.getBalanceAmount())) {
                for (OrderItem oi : payment.getOrder().getOrderItems()) {
                    Long remainingQty = oi.getCancelableQuantity();
                    if (remainingQty > 0) {
                        oi.cancelQuantity(remainingQty);
                        restoreMenuStock(payment.getOrder(), oi, remainingQty);
                    }
                }
            }
        }

        // 5. 결제 잔액 초과 검증
        if (cancelAmount > payment.getBalanceAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED);
        }

        // 6. 토스페이먼츠 취소 API 호출 (cancelRequestId를 Idempotency-Key로 전달)
        try {
            tossPaymentsClient.cancel(
                    paymentKey, new TossCancelRequest(request.cancelReason(), cancelAmount), request.cancelRequestId());
        } catch (Exception e) {
            log.error(
                    "토스 결제 취소 API 호출 실패: paymentKey={}, amount={}, error={}", paymentKey, cancelAmount, e.getMessage());
            throw e;
        }

        // 7. 도메인 잔액 차감, 상태 변경 및 취소 이력 저장
        payment.cancel(
                request.cancelRequestId(),
                targetItem,
                cancelQty,
                itemName,
                cancelAmount,
                request.cancelReason(),
                LocalDateTime.now());

        return PaymentResponse.from(payment);
    }

    private void restoreMenuStock(Order order, OrderItem orderItem, Long quantity) {
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

    private String createOrderName(Order order) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return "CGV 스토어 상품 주문";
        }
        String firstItemName = order.getOrderItems().get(0).getMenu().getName();
        int extraCount = order.getOrderItems().size() - 1;
        return extraCount > 0 ? firstItemName + " 외 " + extraCount + "건" : firstItemName;
    }
}
