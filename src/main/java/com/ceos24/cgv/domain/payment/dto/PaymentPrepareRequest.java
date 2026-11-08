package com.ceos24.cgv.domain.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "결제 준비 요청 DTO (orderId 또는 reservationId 중 하나만 입력)")
public record PaymentPrepareRequest(
        @Min(value = 1, message = "주문 ID는 1 이상이어야 합니다.") @Schema(description = "스토어 주문 ID", example = "1") Long orderId,
        @Min(value = 1, message = "예매 ID는 1 이상이어야 합니다.") @Schema(description = "영화 예매 ID", example = "1")
                Long reservationId) {

    public PaymentPrepareRequest(Long orderId) {
        this(orderId, null);
    }
}
