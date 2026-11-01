package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.order.entity.Order;
import com.ceos24.cgv.domain.order.repository.OrderRepository;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareResponse;
import com.ceos24.cgv.domain.payment.dto.PaymentResponse;
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

    private String createOrderName(Order order) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return "CGV 스토어 상품 주문";
        }
        String firstItemName = order.getOrderItems().get(0).getMenu().getName();
        int extraCount = order.getOrderItems().size() - 1;
        return extraCount > 0 ? firstItemName + " 외 " + extraCount + "건" : firstItemName;
    }
}
