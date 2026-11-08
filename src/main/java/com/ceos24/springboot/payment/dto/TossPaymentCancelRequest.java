package com.ceos24.springboot.payment.dto;

// 재시도해도 금액이 달라지지 않도록 전체 취소도 DB에서 확정한 금액을 명시한다.
public record TossPaymentCancelRequest(String cancelReason, Long cancelAmount) {}
