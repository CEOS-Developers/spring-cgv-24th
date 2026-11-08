package com.ceos24.spring_cgv.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/***
 * 토스페이먼츠 API 실패 응답. 승인 실패 사유를 로그로 남기기 위해 사용한다.
 * ex) { "code": "ALREADY_PROCESSED_PAYMENT", "message": "이미 처리된 결제 입니다." }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentErrorResponse(

        String code,
        String message
) {}
