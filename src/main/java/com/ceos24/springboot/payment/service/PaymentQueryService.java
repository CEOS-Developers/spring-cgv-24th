package com.ceos24.springboot.payment.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.dto.PaymentDetailResponse;
import com.ceos24.springboot.payment.dto.PaymentHistoryResponse;
import com.ceos24.springboot.payment.dto.PaymentCancelResponse;
import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.repository.PaymentCancellationRepository;
import java.util.List;
import com.ceos24.springboot.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {
    private final PaymentRepository payments;
    private final PaymentCancellationRepository cancellations;
    private static final List<PaymentStatus> APPROVED_STATUSES = List.of(
            PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCELED, PaymentStatus.CANCELED);

    public PaymentDetailResponse getCompletedPayment(Long userId, Long paymentId) {
        return PaymentDetailResponse.from(findApprovedPayment(userId, paymentId));
    }

    public PaymentHistoryResponse getCompletedPayments(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_PAGE);
        }
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("approvedAt"), Sort.Order.desc("id")));
        return PaymentHistoryResponse.from(payments.findAllByUser_IdAndStatusInAndApprovedAtIsNotNull(userId, APPROVED_STATUSES, pageable)
                .map(PaymentDetailResponse::from));
    }

    public List<PaymentCancelResponse> getCancellations(Long userId, Long paymentId) {
        Payment payment = findApprovedPayment(userId, paymentId);
        return cancellations.findAllByPayment_IdOrderByIdDesc(paymentId).stream()
                .map(c -> PaymentCancelResponse.from(payment, c)).toList();
    }

    private Payment findApprovedPayment(Long userId, Long paymentId) {
        return payments.findByIdAndUser_IdAndStatusInAndApprovedAtIsNotNull(paymentId, userId, APPROVED_STATUSES)
                .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_NOT_FOUND));
    }
}
