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
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
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
    private final OrderRepository orderRepository;
    private final TossPaymentsClient tossPaymentsClient;

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
        // 1. 주문/결제 데이터 존재성 검증
        Payment payment = paymentRepository
                .findByOrderId(request.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 2. 중복 승인 방어 (상태 검증: READY 상태에서만 승인 가능)
        if (payment.getStatus() != PaymentStatus.READY) {
            log.warn("결제 승인 거절: 이미 처리된 결제입니다. orderId={}, status={}", request.orderId(), payment.getStatus());
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        // 3. 1원 단위 금액 무결성 검증 (위변조 차단)
        if (!payment.getTotalAmount().equals(request.amount())) {
            log.error(
                    "결제 금액 위변조 감지! DB 금액: {}원, 요청 금액: {}원 (orderId={})",
                    payment.getTotalAmount(),
                    request.amount(),
                    request.orderId());
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 4. 모든 무결성 검증 통과 후 비로소 토스페이먼츠 승인 API 호출
        TossPaymentResponse tossResponse;
        try {
            tossResponse = tossPaymentsClient.confirm(
                    new TossConfirmRequest(request.paymentKey(), request.orderId(), request.amount()));
        } catch (Exception e) {
            log.error("토스 결제 승인 API 호출 실패: orderId={}, error={}", request.orderId(), e.getMessage());
            payment.abort();
            throw e;
        }

        // 5. 승인 성공 시 결제 정보(paymentKey, method, approvedAt) DB 영속화 및 상태 DONE 전이
        LocalDateTime approvedAt = tossResponse.approvedAt() != null
                ? OffsetDateTime.parse(tossResponse.approvedAt()).toLocalDateTime()
                : LocalDateTime.now();
        payment.complete(tossResponse.paymentKey(), tossResponse.method(), approvedAt);

        return PaymentResponse.from(payment);
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

        // 2. 이미 전액 취소된 결제 재취소 차단
        if (payment.getStatus() == PaymentStatus.CANCELED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CANCELED);
        }

        OrderItem targetItem = null;
        Long cancelQty = null;
        String itemName = "전체 취소";
        Long cancelAmount;

        // 3. 품목 기반 부분 취소인 경우 vs 전체/금액 기반 취소인 경우
        if (request.orderItemId() != null) {
            targetItem = payment.getOrder().getOrderItems().stream()
                    .filter(oi -> oi.getId().equals(request.orderItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND));

            cancelQty = request.cancelQuantity() != null ? request.cancelQuantity() : 1L;
            itemName = targetItem.getMenu().getName();

            // OrderItem 잔여 수량 검증 및 환불 금액 자동 산출
            cancelAmount = targetItem.cancelQuantity(cancelQty);
        } else {
            // orderItemId가 없으면 금액 기반 취소 또는 잔액 전액 취소
            cancelAmount = request.cancelAmount() != null ? request.cancelAmount() : payment.getBalanceAmount();
            // 전액 취소 시 모든 품목 잔여 수량도 일괄 취소 처리
            if (cancelAmount.equals(payment.getBalanceAmount())) {
                for (OrderItem oi : payment.getOrder().getOrderItems()) {
                    if (oi.getCancelableQuantity() > 0) {
                        oi.cancelQuantity(oi.getCancelableQuantity());
                    }
                }
            }
        }

        // 4. 결제 잔액 초과 검증
        if (cancelAmount > payment.getBalanceAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDED);
        }

        // 5. 토스페이먼츠 취소 API 호출
        try {
            tossPaymentsClient.cancel(paymentKey, new TossCancelRequest(request.cancelReason(), cancelAmount));
        } catch (Exception e) {
            log.error(
                    "토스 결제 취소 API 호출 실패: paymentKey={}, amount={}, error={}", paymentKey, cancelAmount, e.getMessage());
            throw e;
        }

        // 6. 도메인 잔액 차감 & 상태 전이 & 취소 이력 영속화
        payment.cancel(targetItem, cancelQty, itemName, cancelAmount, request.cancelReason(), LocalDateTime.now());

        return PaymentResponse.from(payment);
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
