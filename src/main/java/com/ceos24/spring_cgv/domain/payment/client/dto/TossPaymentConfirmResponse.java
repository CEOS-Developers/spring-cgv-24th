package com.ceos24.spring_cgv.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/***
 * 토스페이먼츠 결제 승인 API 응답. 실제 응답에는 card, virtualAccount, receipt 등
 * 수십 개의 필드가 더 있으므로 @JsonIgnoreProperties로 나머지를 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentConfirmResponse(

        String paymentKey,
        String orderId,
        String orderName,
        String status,              // DONE, CANCELED, WAITING_FOR_DEPOSIT 등
        Integer totalAmount,
        String method,              // "카드", "간편결제" 등 한글 문자열
        OffsetDateTime requestedAt,
        OffsetDateTime approvedAt,
        String lastTransactionKey
) {}
