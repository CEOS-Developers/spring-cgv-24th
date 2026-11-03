package com.ceos24.springboot.payment.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.dto.PaymentDetailResponse;
import com.ceos24.springboot.payment.dto.PaymentHistoryResponse;
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

    public PaymentDetailResponse getCompletedPayment(Long userId, Long paymentId) {
        return payments.findByIdAndUser_IdAndStatus(paymentId, userId, PaymentStatus.DONE)
                .map(PaymentDetailResponse::from)
                .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    public PaymentHistoryResponse getCompletedPayments(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new GlobalException(ErrorCode.INVALID_PAYMENT_PAGE);
        }
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("approvedAt"), Sort.Order.desc("id")));
        return PaymentHistoryResponse.from(payments.findAllByUser_IdAndStatus(userId, PaymentStatus.DONE, pageable)
                .map(PaymentDetailResponse::from));
    }
}
