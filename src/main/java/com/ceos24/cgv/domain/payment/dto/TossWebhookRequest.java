package com.ceos24.cgv.domain.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "토스페이먼츠 웹훅 요청 DTO")
public record TossWebhookRequest(
        @Schema(description = "웹훅 이벤트 타입", example = "PAYMENT_STATUS_CHANGED") String eventType,
        @Schema(description = "이벤트 발생 시각") String createdAt,
        @Schema(description = "결제 상태 변경 상세 데이터") TossWebhookData data,
        @Schema(description = "가상계좌 콜백 검증용 시크릿 (DEPOSIT_CALLBACK)") String secret,
        @Schema(description = "주문번호 (DEPOSIT_CALLBACK 호환)") String orderId,
        @Schema(description = "결제 상태 (DEPOSIT_CALLBACK 호환)") String status,
        @Schema(description = "결제 키 (일부 이벤트 호환)") String paymentKey) {

    public String resolvedPaymentKey() {
        if (data != null && data.paymentKey() != null && !data.paymentKey().isBlank()) {
            return data.paymentKey();
        }
        return paymentKey;
    }

    public String resolvedOrderId() {
        if (data != null && data.orderId() != null && !data.orderId().isBlank()) {
            return data.orderId();
        }
        return orderId;
    }

    public String resolvedStatus() {
        if (data != null && data.status() != null && !data.status().isBlank()) {
            return data.status();
        }
        return status;
    }

    public String resolvedSecret() {
        if (data != null && data.secret() != null && !data.secret().isBlank()) {
            return data.secret();
        }
        return secret;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TossWebhookData(
            String paymentKey,
            String orderId,
            String status,
            String secret,
            String method,
            Long totalAmount,
            Long balanceAmount,
            String approvedAt,
            List<TossPaymentResponse.TossCancel> cancels) {}
}
