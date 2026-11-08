package com.ceos24.cgv.domain.payment.dto.response;

// 브라우저 SDK 초기화에 필요한 공개 키만 전달한다. Properties 전체를 응답하지 않는다.
public record PaymentCheckoutConfigResponse(String clientKey) {
}
