package com.ceos24.springboot.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentConfirmResponse(
        String paymentKey, // 승인 결과의 결제 키
        String orderId, // 승인 결과의 주문번호
        Long totalAmount, // 총 결제 금액
        String orderName,// 결제 상품명
        String status,// 토스에서 관리하는 결제 상태
        String method, // 결제 수단
        OffsetDateTime approvedAt // 승인 완료 시각
) {
}