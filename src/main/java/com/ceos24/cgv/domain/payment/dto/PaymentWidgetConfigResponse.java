package com.ceos24.cgv.domain.payment.dto;

// 브라우저가 결제위젯 SDK를 초기화할 때 쓰는 값만 담는다. 시크릿 키는 절대 여기에 넣지 않는다.
public record PaymentWidgetConfigResponse(String clientKey) {
}
