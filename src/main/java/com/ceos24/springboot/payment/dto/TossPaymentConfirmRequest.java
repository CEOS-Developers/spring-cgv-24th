package com.ceos24.springboot.payment.dto;

// 우리 서버 → 토스
// 검증한 DB 정보를 사용해 생성
public record TossPaymentConfirmRequest(
        String paymentKey, // 인증 후 토스에서 전달받은 결제 키
        String orderId, // 결제 준비 단계에서 우리 서버가 생성한 주문번호
        Long amount// DB의 결제 금액으로 검증한 승인 요청 금액
) {
}