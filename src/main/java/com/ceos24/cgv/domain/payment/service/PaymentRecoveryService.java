package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link PaymentTransactionService}로 역할이 통합됨. 테스트 정리 단계 완료 후 제거 예정.
 */
@Deprecated(forRemoval = true)
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final PaymentRepository paymentRepository;
}
